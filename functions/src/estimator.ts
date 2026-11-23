// Mirror of app/src/main/java/com/sabil/qnow/domain/queue/WaitEstimator.kt. Keep the two in step.

export interface TokenLite {
  id: string;
  number: number;
  status: string;
  durationMinutes: number;
  calledAtMillis: number | null;
}

export interface Estimate {
  position: number;
  peopleAhead: number;
  waitMinutes: number;
}

const IN_CHAIR = new Set(["SERVING", "GRACE"]);

export function estimate(tokens: TokenLite[], activeChairs: number, nowMillis: number): Map<string, Estimate> {
  const inChair = tokens.filter((t) => IN_CHAIR.has(t.status));
  const waiting = tokens.filter((t) => t.status === "WAITING").sort((a, b) => a.number - b.number);

  const freeAt = new Array<number>(Math.max(1, activeChairs)).fill(0);
  inChair
    .map((t) => remaining(t, nowMillis))
    .sort((a, b) => b - a)
    .forEach((m, i) => (freeAt[i % freeAt.length] += m));

  const result = new Map<string, Estimate>();
  waiting.forEach((t, index) => {
    let slot = 0;
    for (let i = 1; i < freeAt.length; i++) if (freeAt[i] < freeAt[slot]) slot = i;
    result.set(t.id, {
      position: index + 1,
      peopleAhead: index + inChair.length,
      waitMinutes: Math.ceil(freeAt[slot]),
    });
    freeAt[slot] += t.durationMinutes;
  });
  return result;
}

function remaining(t: TokenLite, now: number): number {
  if (t.calledAtMillis == null) return t.durationMinutes;
  const elapsed = (now - t.calledAtMillis) / 60000;
  return Math.min(Math.max(1, t.durationMinutes - elapsed), Math.max(1, t.durationMinutes));
}

export function dayKey(nowMillis: number, utcOffsetMinutes: number): number {
  return Math.floor((nowMillis + utcOffsetMinutes * 60000) / 86400000);
}
