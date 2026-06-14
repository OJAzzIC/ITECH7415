package vocab;

/*
 * A named parent-agent profile, loaded from a [[parent_profile]] section of
 * simulation.conf.
 *
 * pool: the name of the [[utterance_pool]] this parent samples its speech
 * from (e.g. an ESL corpus).  "default" is the pool built from the standard
 * environment.locations.utterances directory.
 *
 * dailyWordsFactor: multiplier applied to the household's SES daily word
 * budget (1.0 = speak the full budget).
 */
public record ParentProfile(String name, String pool, double dailyWordsFactor) {

    public static ParentProfile standard() {
        return new ParentProfile("standard", "default", 1.0);
    }
}
