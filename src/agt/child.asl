// Include all beliefs, goals & plans common to all agents
{ include("inc/common.asl") }

/*********************************************************************************
 * Initial beliefs & goals                                                       *
 * Set by the launcher class - refer to the addChildAgents() method for details. *
 *********************************************************************************/

/*****************
 * Initial plans *
 *****************/

// This trio of plans checks to see if the agent has 'heard' the word already.
+!word_heard_checker(Word)<-
    ?heard::word(Word,Count);   // Check to see if we've already seen the word
    -heard::word(Word,Count);   // Remove the belief that we've seen the word
    +heard::word(Word,Count+1); // Add the belief that we've seen the word an extra time.
    .
+?heard::word(Word,Count)<-                 // This plan handles when the word is being seen for the 1st time
    Count=0;                                //
    +heard::word(Word,Count);               // Add the 'base' belief, so that the above plan can complete.
    ?heard::unique_words(UniqueCount);      // Check to see if we've got a 'unique_words' belief
    -heard::unique_words(UniqueCount);      // Remove that
    +heard::unique_words(UniqueCount+1);    // Add it back, with an incremented value
    .
+?heard::unique_words(UniqueCount)<-    // This plan handles when the agent hears their 1st word
    UniqueCount=0;
    +heard::unique_words(UniqueCount);  // Add the 'base' belief, so that the above plans can complete.
    .

// This trio of plans does the same as the above, but for words 'seen'.
+!word_seen_checker(Word)<-
    ?seen::word(Word,Count);
    -seen::word(Word,Count);
    +seen::word(Word,Count+1);
    .
+?seen::word(Word,Count)<-
    Count=0;
    +seen::word(Word,Count);
    ?seen::unique_words(UniqueCount);
    -seen::unique_words(UniqueCount);
    +seen::unique_words(UniqueCount+1);
    .
+?seen::unique_words(UniqueCount)<-
    UniqueCount=0;
    +seen::unique_words(UniqueCount);
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

+!try_learn_word(Word)<-
    .my_name(Me);
    learnt::hasLearntWord(Me,Word,Learned);
    if(not Learned){
        ?heard::word(Word,HeardCount);
        ?seen::word(Word,SeenCount);
        if((HeardCount>12 & SeenCount>12)|(HeardCount>20)|(SeenCount>20)){
            ?age(Age);
            ?home::ses(Ses,_);
            learnt::learnWord(Me,Ses,Age,Word);
        };
    };
    .
+?heard::word(_,_).
+?seen::word(_,_).

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
// both that nothing else it attempted while running this plan AND that the
// agent has actually finished processing all words seen or heard.
@[atomic]
+sync::newYear<-
    .wait(activityState("Idle"));
    .my_name(Me);
    .print("Doing end-of-year duties...");
    ?age(Age);
    ?home::ses(Ses,_);
    ?seen::unique_words(WordsSeenCount);
    ?heard::unique_words(WordsHeardCount);
    learnt::numWordsKnown(Me,WordsLearntCount);
    addAnnualStats(Me,Ses,Age,WordsSeenCount,WordsHeardCount,WordsLearntCount);
    .print("Completed end-of-year duties...");
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
/* The plan below is used to customise the askOne performative.
   askOne (and askAll) normally just consult the agent's belief base.
   With this customisation, the answer can be something not in the belief base
   (e.g., the result of some operations) and not handled by +? events. This
   customisation is applied only when the content of the askOne message matches
   "number_words_read(Title)".
   'kqml_received' MUST not be changed, otherwise this won't get triggered.
   1st term is the sending agent.
   2nd term is the 'performative' verb received.
   3rd term is the request.
   4th term, optional, is the 'response' field.
*/
+!kqml_received(Sender,askOne,number_words_read(Title),Count):school::my_teacher(Teacher) & Sender==Teacher<-
    ?words_read(Title,WordCount);
    .send(Sender,tell,WordCount,Count);
    .
