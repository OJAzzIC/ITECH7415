package vocab;

/*
 * A named home-environment profile, loaded from a [[home_profile]] section
 * of simulation.conf.
 *
 * booksPerDay: how many randomly chosen books the parent reads aloud to the
 * child at home each day (0 = no reading at home, the baseline behaviour).
 * Home books are read aloud, so their words count as 'heard'.
 */
public record HomeProfile(String name, int booksPerDay) {

    public static HomeProfile noBooks() {
        return new HomeProfile("no_books", 0);
    }
}
