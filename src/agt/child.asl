// Include all beliefs, goals & plans common to all agents
{ include("inc/common.asl") }

/*********************************************************************************
 * Initial beliefs & goals                                                       *
 * Set by the launcher class - refer to the addChildAgents() method for details. *
 *********************************************************************************/
words::unique_seen(0).
words::unique_heard(0).

/*****************
 * Initial plans *
 *****************/

// This plan incremements the number of times a given word has been 'heard'.
// Additionally, if this is the 1st time that word has been 'heard', increment
// the counter which tracks how many unique words have been heard.
+!word_heard_checker(Word)<-
    ?words::word(Word,[Seen,Heard,AgeLearned]);
    -words::word(Word,_);
    +words::word(Word,[Seen,Heard+1,AgeLearned]);
    if(Heard==0){
        ?words::unique_heard(Count);
        -+words::unique_heard(Count+1);
    };
    .

// This plan does the same as above, but for a word 'seen'.
+!word_seen_checker(Word)<-
    ?words::word(Word,[Seen,Heard,AgeLearned]);
    -words::word(Word,_);
    +words::word(Word,[Seen+1,Heard,AgeLearned]);
    if(Seen==0){
        ?words::unique_seen(Count);
        -+words::unique_seen(Count+1);
    };
    .

// This plan gets the agent to try to 'learn' the word.
// The criteria are that the word has been:
//  heard at least 20 times; or
//  seen at least 20 times; or
//  both seen and heard at least 12 times each.
+!try_learn_word(Word)<-
    ?words::word(Word,[Seen,Heard,AgeLearned]);
    if(AgeLearned==0){
        if((Seen>12 & Heard>12)|Seen>20|Heard>20){
            ?age(Age);
            -words::word(Word,_);
            +words::word(Word,[Seen,Heard,Age]);
            .my_name(Me);
            ?home::ses(Ses,_);
            learnt::learnWord(Me,Ses,Age,Word);
        };
    };
    .

// In the above 3 plans, a check is made to see if a word is present in the belief base.
// The 1st time that check is made for a given word, this plan will be executed.
// This plan creates the starting belief about the given word.
+?words::word(Word,[Seen,Heard,AgeLearned])<-
    Seen=0; Heard=0; AgeLearned=0;
    +words::word(Word,[Seen,Heard,AgeLearned]);
    .

// Setting the agents 'state' is done regularly, so this is a dedicated plan
// to handle settings the state.
+!setState(State)<-
    -activityState(_);
    +activityState(State);
    .

// 'Agent' is the agent which gave us the goal 'read_a_book(Title)'.
// 'Title' is the title of the book, naturally enough.
// 'Teacher' is the agent playing 'teacher' role within the class this agent is a member of.
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
            !word_seen_checker(Word);
            !try_learn_word(Word);
        };
        .send(Teacher,tell,finished(Title));
        !setState("Idle");
    }
    .

// The custom launcher tells the 'parent' agents to create groups representing
// homes/family units, and tells this child agent which one to join, to play
// the role of 'offspring'.
+!join_home_group(HomeGroup)<-
    .wait(group(village,village,VillageGroupID));
    .wait(subgroups(Homes)[artifact_id(VillageGroupID)]);
    .wait(group(HomeGroup,indv_home,GroupID));
    // The above lines ensure that the homegroup we have been told to join does
    // exist before we attempt to join it.
    home::focus(GroupID);
    home::adoptRole(offspring)[artifact_name(HomeGroup),wsp(learning_environ)];
    .

// The custom launcher gives this child agent the goal of 'join_school'.
// There probably is a way to apply the 'school' namespace to this using Java
// or Moise, but this works AND allows for the possibility of coding a child
// agent to be truant.
+!join_school<-
    //.print("Trying to join the 'classroom'...");
    .wait(group(Class,classroom,ClassID));
    //.print("Found the classroom. Yay???");
    school::focus(ClassID);
    school::adoptRole(student)[artifact_name(Class),wsp(learning_environ)];
    .

// A parent agent has said an utterance to this child agent.
@[atomic]
+!listen_to_speech(Utterance)[source(Parent)]<-
    !setState("Busy - Listening");
    for(.member(Word,Utterance)){
        !word_heard_checker(Word);
        !try_learn_word(Word);
    };
    .send(Parent,tell,finishedUtterance);
    !setState("Idle");
    .

@[atomic]
+!listen_to_speeches(Utterances,Counter)[source(Parent)]<-
    !setState("Busy - Listening");
    for(.member(Utterance,Utterances)){
        for(.member(Word,Utterance)){
            !word_heard_checker(Word);
            !try_learn_word(Word);
        };
    };
    .send(Parent,tell,finishedUtterances(Counter));
    !setState("Idle");
    .
// The synchroniser artefact 'signals' the end of each year (by raising the
// 'newYear' signal).  This causes all agents focusing on that artefact to gain
// this belief.  By being atomic, and having the '.wait' statement, we ensure
// both that nothing else is attempted while running this plan AND that the
// agent has actually finished processing all words seen or heard.
@[atomic]
+sync::newYear<-
    .wait(activityState("Idle"));
    .my_name(Me);
    ?age(Age);
    ?home::ses(Ses,_);
    ?words::unique_seen(WordsSeenCount);
    ?words::unique_heard(WordsHeardCount);
    learnt::numWordsKnown(Me,WordsLearntCount);
    addAnnualStats(Me,Ses,Age,WordsSeenCount,WordsHeardCount,WordsLearntCount);
    .

// A little back-and-forth plan to get the synchroniser to wait until all child
// agents have finished everything.
@[atomic]
+sync::finalise<-
    .wait(activityState("Idle"));
    childAgentFinalised;
    .

// The synchroniser altered it's 'agent_age()' Observable Property, and this
// agent noticed
@[atomic]
+sync::agent_age(NewAge)<-
    // Make sure there's nothing else happening
    .wait(activityState("Idle"));
    !setState("Busy - Observed a change in the Synchroniser's state");
    ?age(Age);
    // See if the synchroniser has actually changed the age.
    if(NewAge\==Age){
        -age(_);
        +age(NewAge);
    }
    !setState("Idle");
    .
