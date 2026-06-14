// Include all beliefs, goals & plans common to all child agents
{ include("inc/child_common.asl") }

/*********************************************************************************
 * Initial beliefs & goals                                                       *
 * Set by the launcher class - refer to the addChildAgents() method for details. *
 * The launcher injects, per the child's [[child_profile]]:                      *
 *   profile("name")                                                             *
 *   attentiveness_heard(F)  attentiveness_seen(F)                               *
 *   thresholds(Seen,Heard,Both)                                                 *
 * The test-goal plans below supply the original model's defaults if the        *
 * launcher injected nothing (e.g. agents created directly from the .jcm).      *
 *********************************************************************************/
+?profile(P)<- P = "typical".
+?attentiveness_heard(F)<- F = 1.0.
+?attentiveness_seen(F)<- F = 1.0.
+?thresholds(TS,TH,TB)<- TS = 20; TH = 20; TB = 12.

/*****************
 * Initial plans *
 *****************/

// This plan incremements the number of times a given word has been 'heard'.
// Additionally, if this is the 1st time that word has been 'heard', increment
// the counter which tracks how many unique words have been heard.
+!word_heard_checker(Word)<-
    ?words::word(Word,[Seen,Heard,AgeLearned]);
    if(Heard==0 & Seen==0){
        ?words::unique_encountered(EncCount);
        -+words::unique_encountered(EncCount+1);
    };
    if(Heard==0){
        ?words::unique_heard(Count);
        -+words::unique_heard(Count+1);
    };
    +words::word(Word,[Seen,Heard+1,AgeLearned]);
    .

// This plan does the same as above, but for a word 'seen'.
+!word_seen_checker(Word)<-
    ?words::word(Word,[Seen,Heard,AgeLearned]);
    if(Seen==0 & Heard==0){
        ?words::unique_encountered(EncCount);
        -+words::unique_encountered(EncCount+1);
    };
    if(Seen==0){
        ?words::unique_seen(Count);
        -+words::unique_seen(Count+1);
    };
    +words::word(Word,[Seen+1,Heard,AgeLearned]);
    .

// This plan gets the agent to try to 'learn' the word.
// The default criteria (thresholds(20,20,12)) are that the word has been:
//  heard more than 20 times; or
//  seen more than 20 times; or
//  both seen and heard more than 12 times each.
// The thresholds come from the child's profile so different learner types
// can be modelled; the defaults reproduce the original rule exactly.
+!try_learn_word(Word)<-
    ?words::word(Word,[Seen,Heard,AgeLearned]);
    if(AgeLearned==0){
        ?thresholds(TSeen,THeard,TBoth);
        if((Seen>TBoth & Heard>TBoth)|Seen>TSeen|Heard>THeard){
            ?age(Age);
            +words::word(Word,[Seen,Heard,Age]);
        };
    };
    .

// Attentiveness gates: the child attends to any single heard/seen word with
// the profile's channel-specific probability (1.0 = attends to everything).
+!maybe_process_heard(Word)<-
    ?attentiveness_heard(F);
    .random(R);
    if (R < F) {
        !word_heard_checker(Word);
        !try_learn_word(Word);
    }.

+!maybe_process_seen(Word)<-
    ?attentiveness_seen(F);
    .random(R);
    if (R < F) {
        !word_seen_checker(Word);
        !try_learn_word(Word);
    }.


// 'Agent' is the agent which gave us the goal 'read_a_book(Title)'.
// 'Title' is the title of the book, naturally enough.
// 'Teacher' is the agent playing 'teacher' role within the class this agent is a member of.
@[atomic]
+!read_a_book(Title)[source(Agent)]:school::my_teacher(Teacher) & Agent==Teacher <-
    get_bookByTitle(Title,Text);
//  .length(Text,WordCount) // DO NOT USE WITH LOOOOONG TEXTS
                            // A StackOverflow exception awaits you!
                            // Get Java to handle it directly
    getWordCountByBookTitle(Title,WordCount);
    if(WordCount>0){
        // The book has some words in it, so lets 'read' it.
        !setState("Busy - Reading");
        for(.member(Word,Text)){
            if(not .length(Word,0)){
                !maybe_process_seen(Word);
            };
        };
        .send(Teacher,tell,finished(Title));
        !setState("Idle");
    }
    .


// Another agent has said something to this child agent.
@[atomic]
+listen_to_speech(Words)[source(Other)]<-
    !setState("Busy - Listening");
    for(.member(Word,Words)){
        !maybe_process_heard(Word);
    };
    .send(Other,tell,finishedListening);
    -listen_to_speech(Words)[source(Other)];
    !setState("Idle");
    .

// Process one batch of utterances from the parent, then report it to the
// synchroniser artifact.  The report happens only after the batch is fully
// processed, so when the artifact has counted a report for every batch the
// parent sent, the parent knows the child has heard everything for the day.
// Each word goes through the attentiveness gate, so inattentive profiles
// also attend to only some of the conversation at home.
@[atomic]
+listen_to_utterances(Utterances,Counter)[source(Parent)]<-
    !setState("Busy - Listening");
    for(.member(Utterance,Utterances)){
        for(.member(Word,Utterance)){
            !maybe_process_heard(Word);
        };
    };
    -listen_to_utterances(_,Counter)[source(Parent)];
    sync::utteranceBatchProcessed;
    .

// A parent is reading a book aloud to this child at home (home_profile
// books_per_day > 0).  Read-aloud words count as 'heard', through the same
// attentiveness gate as conversation.  The batch report at the end feeds
// the same day-end handshake as utterance batches.
@[atomic]
+listen_to_home_book(Text)[source(Parent)]<-
    !setState("Busy - Listening");
    for(.member(Word,Text)){
        if(not .length(Word,0)){
            !maybe_process_heard(Word);
        };
    };
    -listen_to_home_book(_)[source(Parent)];
    sync::utteranceBatchProcessed;
    .

// The parent has confirmed that every batch it sent today was processed,
// so the day's listening is definitely over - go idle.
// (The previous design had the child count batches against a fixed total
// of 20; a batch arriving after the count was satisfied left the child
// stuck in 'Busy - Listening' forever, which hung the daily cycle and the
// end-of-simulation finalisation.)
+day_done[source(Parent)]<-
    -day_done[source(Parent)];
    !setState("Idle");
    .
