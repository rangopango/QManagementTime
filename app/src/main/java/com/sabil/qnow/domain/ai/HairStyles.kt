package com.sabil.qnow.domain.ai

/** A look a customer can preview on their own photo. [prompt] is what the AI is asked to do. */
data class StyleOption(val id: String, val name: String, val note: String, val prompt: String)

data class StyleGroup(val id: String, val title: String, val options: List<StyleOption>)

private fun cut(id: String, name: String, note: String, detail: String) =
    StyleOption(id, name, note, "Change only the hair into a $name haircut: $detail.")

private fun colour(id: String, name: String, note: String, detail: String) =
    StyleOption(id, name, note, "Change only the hair colour to $detail. Keep the current haircut shape.")

/**
 * The style catalogue. Haircuts and colours are separate so a customer can combine them
 * (e.g. "Low fade" + "Ash grey"). Prompts describe the look plainly; the AI keeps the face as is.
 */
object HairStyles {
    val men = StyleGroup("men", "Men", listOf(
        cut("buzz", "Buzz cut", "Very short all over", "clipped very short and even all over, about 3 mm"),
        cut("crew", "Crew cut", "Short sides, a little length on top", "short tapered sides with slightly longer top"),
        cut("low_fade", "Low fade", "Fade starting low above the ears", "a low skin fade with short textured top"),
        cut("mid_fade", "Mid fade", "Fade from the temples", "a mid skin fade with a neat textured top"),
        cut("high_fade", "High fade", "Sharp, bold fade", "a high skin fade with a short top"),
        cut("taper", "Taper", "Gradual, natural taper", "a classic taper with natural length on top"),
        cut("undercut", "Undercut", "Short sides, long top", "a disconnected undercut with longer swept-back top"),
        cut("pompadour", "Pompadour", "Volume swept up and back", "a pompadour with high volume at the front and short sides"),
        cut("quiff", "Quiff", "Front styled up", "a modern quiff with the fringe styled upward"),
        cut("side_part", "Side part", "Smart classic parting", "a neat side part with short tapered sides"),
        cut("slick_back", "Slick back", "Combed straight back", "slicked-back medium length hair with a polished finish"),
        cut("french_crop", "French crop", "Short fringe, faded sides", "a French crop with a short straight fringe and faded sides"),
        cut("caesar", "Caesar cut", "Short, even forward fringe", "a Caesar cut with a short horizontal fringe"),
        cut("ivy", "Ivy League", "Longer crew cut", "an Ivy League cut, long enough on top to part"),
        cut("faux_hawk", "Faux hawk", "Centre strip styled up", "a faux hawk with the centre styled up and shorter sides"),
        cut("mohawk", "Mohawk", "Bold centre strip", "a mohawk with shaved sides and a tall centre strip"),
        cut("textured_fringe", "Textured fringe", "Messy forward fringe", "a textured crop with a messy forward fringe"),
        cut("curly_top", "Curly top", "Curls on top, short sides", "defined curls on top with a short faded back and sides"),
        cut("modern_mullet", "Modern mullet", "Short front, longer back", "a modern mullet, short at the front and sides, longer at the back"),
        cut("spiky", "Spiky", "Short spikes", "short spiky hair styled with gel"),
        cut("long_layers_m", "Long layered", "Shoulder length", "shoulder-length layered hair"),
        cut("man_bun", "Man bun", "Long hair tied up", "long hair tied into a neat man bun"),
        cut("afro", "Afro", "Full natural volume", "a rounded natural afro"),
        cut("cornrows", "Cornrows", "Neat braided rows", "neat straight-back cornrow braids"),
        cut("dreadlocks", "Dreadlocks", "Medium locs", "medium length dreadlocks"),
    ))

    val women = StyleGroup("women", "Women", listOf(
        cut("pixie", "Pixie cut", "Short and chic", "a short pixie cut"),
        cut("bixie", "Bixie", "Between a bob and a pixie", "a bixie cut with soft layers"),
        cut("bob", "Classic bob", "Chin length", "a chin-length classic bob"),
        cut("lob", "Lob", "Long bob to the shoulders", "a shoulder-length long bob"),
        cut("asym_bob", "Asymmetric bob", "One side longer", "an asymmetric bob, longer on one side"),
        cut("curly_bob", "Curly bob", "Short with curls", "a curly bob with defined curls"),
        cut("long_layers", "Long layers", "Soft movement", "long hair with soft face-framing layers"),
        cut("butterfly", "Butterfly cut", "Bouncy layers", "a butterfly cut with voluminous short and long layers"),
        cut("shag", "Shag", "Choppy layers", "a modern shag with choppy layers"),
        cut("wolf", "Wolf cut", "Shag meets mullet", "a wolf cut with heavy layers and volume at the crown"),
        cut("curtain_bangs", "Curtain bangs", "Parted fringe", "long hair with curtain bangs parted in the middle"),
        cut("blunt_bangs", "Blunt bangs", "Straight full fringe", "a straight blunt fringe with long straight hair"),
        cut("feathered", "Feathered", "Soft flicked layers", "feathered layers flicked away from the face"),
        cut("beach_waves", "Beach waves", "Loose waves", "long loose beach waves"),
        cut("sleek_straight", "Sleek straight", "Glossy and straight", "long sleek straight glossy hair"),
        cut("high_pony", "High ponytail", "Pulled up high", "hair pulled into a sleek high ponytail"),
        cut("bun", "Top bun", "Neat bun", "hair styled into a neat top bun"),
        cut("braid_crown", "Braided crown", "Braid around the head", "a braided crown around the head"),
        cut("french_braid", "French braid", "Single classic braid", "a single French braid down the back"),
    ))

    val kids = StyleGroup("kids", "Kids", listOf(
        cut("kids_crew", "Kids crew", "Easy and neat", "a short neat children's crew cut"),
        cut("kids_fade", "Kids fade", "Trendy fade", "a children's low fade with short top"),
        cut("kids_undercut", "Kids undercut", "Longer top", "a children's undercut with a longer top"),
        cut("bowl", "Bowl cut", "Rounded fringe", "a rounded bowl cut"),
        cut("kids_bob", "Little bob", "Short bob", "a short children's bob"),
        cut("pigtails", "Pigtails", "Two bunches", "two neat pigtails"),
        cut("kids_braids", "Braids", "Two plaits", "two neat plaits"),
    ))

    val beard = StyleGroup("beard", "Beard", listOf(
        StyleOption("clean", "Clean shave", "No facial hair", "Remove all facial hair for a clean shave. Do not change the hair on the head."),
        StyleOption("stubble", "Stubble", "Light stubble", "Give the person a short even stubble beard. Do not change the hair on the head."),
        StyleOption("short_boxed", "Short boxed beard", "Neat and trimmed", "Give the person a neatly trimmed short boxed beard. Do not change the hair on the head."),
        StyleOption("full_beard", "Full beard", "Thick and groomed", "Give the person a full, well-groomed beard. Do not change the hair on the head."),
        StyleOption("goatee", "Goatee", "Chin beard", "Give the person a neat goatee. Do not change the hair on the head."),
        StyleOption("van_dyke", "Van Dyke", "Goatee + moustache", "Give the person a Van Dyke beard with a separate moustache. Do not change the hair on the head."),
        StyleOption("handlebar", "Handlebar moustache", "Curled moustache", "Give the person a handlebar moustache. Do not change the hair on the head."),
    ))

    val colours = StyleGroup("colour", "Colour & dye", listOf(
        colour("black", "Natural black", "Deep black", "a natural deep black"),
        colour("dark_brown", "Dark brown", "Rich brown", "a rich dark brown"),
        colour("chestnut", "Chestnut", "Warm brown", "a warm chestnut brown"),
        colour("burgundy", "Burgundy", "Wine red", "a deep burgundy wine red"),
        colour("copper", "Copper red", "Bright copper", "a bright copper red"),
        colour("henna", "Henna", "Natural henna red", "a natural henna orange-red tint"),
        colour("honey", "Honey blonde", "Warm blonde", "a warm honey blonde"),
        colour("platinum", "Platinum blonde", "Icy blonde", "an icy platinum blonde"),
        colour("ash_grey", "Ash grey", "Cool grey", "a cool ash grey"),
        colour("silver", "Silver", "Bright silver", "a bright metallic silver"),
        colour("highlights", "Highlights", "Light streaks", "the current colour with soft caramel highlights"),
        colour("balayage", "Balayage", "Hand-painted fade", "a caramel balayage, darker at the roots and lighter at the ends"),
        colour("ombre", "Ombre", "Dark to light", "an ombre fading from dark roots to light blonde ends"),
        colour("pastel_pink", "Pastel pink", "Soft pink", "a soft pastel pink"),
        colour("blue", "Electric blue", "Bold blue", "a bold electric blue"),
        colour("purple", "Violet", "Rich purple", "a rich violet purple"),
    ))

    val groups = listOf(men, women, kids, beard, colours)

    /** Instruction for any mix of a haircut, a beard and a colour picked together. */
    fun promptFor(picks: List<StyleOption>): String = buildString {
        append("Edit this photo of a real person to preview a new look at a salon. ")
        picks.forEach { append(it.prompt).append(' ') }
        append("Keep the person's face, identity, expression, skin tone, age, clothing, pose, lighting and background exactly the same. ")
        append("Make it photorealistic, like a real photo taken after the visit. Return one edited image.")
    }

    /** The instruction sent with the customer's photo. Identity-preserving and realistic. */
    fun prompt(cut: StyleOption?, colour: StyleOption?): String = buildString {
        append("Edit this photo of a real person to preview a new look at a salon. ")
        cut?.let { append(it.prompt).append(' ') }
        colour?.let { append(it.prompt).append(' ') }
        append("Keep the person's face, identity, expression, skin tone, age, clothing, pose, lighting and background exactly the same. ")
        append("Make it photorealistic, like a real photo taken after the visit. Return one edited image.")
    }
}
