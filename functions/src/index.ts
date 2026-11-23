import { initializeApp } from "firebase-admin/app";
import { FieldValue, getFirestore, Timestamp } from "firebase-admin/firestore";
import { getMessaging } from "firebase-admin/messaging";
import { onDocumentWritten } from "firebase-functions/v2/firestore";
import { onSchedule } from "firebase-functions/v2/scheduler";
import { logger } from "firebase-functions/v2";
import { dayKey, estimate, TokenLite } from "./estimator";

initializeApp();
const db = getFirestore();

type Alert = "15" | "5" | "turn";

/** Re-check a business's queue whenever any of its tokens changes. */
export const onTokenWrite = onDocumentWritten("businesses/{bizId}/tokens/{tokenId}", async (event) => {
  await evaluate(event.params.bizId);
});

/** Waits shrink as time passes even without writes, so also sweep open businesses regularly. */
export const sweepQueues = onSchedule("every 2 minutes", async () => {
  const open = await db.collection("businesses").where("status", "in", ["OPEN", "ON_BREAK"]).get();
  const now = Date.now();
  const active = open.docs.filter((d) => d.get("queue.dayKey") === dayKey(now, d.get("utcOffsetMinutes") ?? 0));
  await Promise.all(active.map((d) => evaluate(d.id)));
});

async function evaluate(bizId: string): Promise<void> {
  const bizSnap = await db.doc(`businesses/${bizId}`).get();
  if (!bizSnap.exists) return;
  const now = Date.now();
  const today = dayKey(now, bizSnap.get("utcOffsetMinutes") ?? 0);
  const businessName: string = bizSnap.get("name") ?? "";
  const activeChairs: number = bizSnap.get("activeChairs") ?? bizSnap.get("chairs") ?? 1;

  const snap = await db.collection(`businesses/${bizId}/tokens`)
    .where("dayKey", "==", today)
    .where("status", "in", ["WAITING", "SERVING", "GRACE"])
    .orderBy("number")
    .get();
  const tokens: TokenLite[] = snap.docs.map((d) => ({
    id: d.id,
    number: d.get("number"),
    status: d.get("status"),
    durationMinutes: d.get("durationMinutes") ?? 20,
    calledAtMillis: (d.get("calledAt") as Timestamp | undefined)?.toMillis() ?? null,
  }));
  const estimates = estimate(tokens, activeChairs, now);

  await Promise.all(tokens.map(async (t) => {
    let due: Alert | null = null;
    let alsoMark: Alert[] = [];
    if (t.status === "SERVING" || t.status === "GRACE") {
      due = "turn";
      alsoMark = ["15", "5"];
    } else {
      const wait = estimates.get(t.id)?.waitMinutes;
      if (wait == null) return;
      if (wait <= 5) { due = "5"; alsoMark = ["15"]; }
      else if (wait <= 15) due = "15";
    }
    if (due) await sendOnce(bizId, businessName, t, due, alsoMark);
  }));
}

/** Marks the alert as sent in a transaction first, so overlapping triggers can't send it twice. */
async function sendOnce(bizId: string, businessName: string, t: TokenLite, alert: Alert, alsoMark: Alert[]) {
  const holderRef = db.doc(`businesses/${bizId}/holders/${t.id}`);
  const customerId = await db.runTransaction(async (tx) => {
    const holder = await tx.get(holderRef);
    const uid: string | null = holder.get("customerId") ?? null;
    const sent = (holder.get("alerts") ?? {}) as Record<string, boolean>;
    if (!holder.exists || !uid || sent[alert]) return null;
    const update: Record<string, boolean> = { [`alerts.${alert}`]: true };
    alsoMark.forEach((a) => (update[`alerts.${a}`] = true));
    tx.update(holderRef, update);
    return uid;
  });
  if (!customerId) return;

  const user = await db.doc(`users/${customerId}`).get();
  const fcmTokens: string[] = user.get("fcmTokens") ?? [];
  if (fcmTokens.length === 0) return;

  // Data-only message: the app builds the notification. Ids and the number only; no personal data.
  const response = await getMessaging().sendEachForMulticast({
    tokens: fcmTokens,
    data: { alert, businessId: bizId, tokenId: t.id, number: String(t.number), businessName },
    android: { priority: "high", ttl: 15 * 60 * 1000 },
  });
  const stale = response.responses
    .map((r, i) => (!r.success && r.error?.code === "messaging/registration-token-not-registered" ? fcmTokens[i] : null))
    .filter((x): x is string => x != null);
  if (stale.length > 0) {
    await user.ref.update({ fcmTokens: FieldValue.arrayRemove(...stale) });
  }
  logger.info("queue alert sent", { bizId, tokenId: t.id, alert, delivered: response.successCount });
}
