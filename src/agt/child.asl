// Include all beliefs, goals & plans common to all agents
{ include("inc/common.asl") }

/*********************************************************************************
 * Initial beliefs & goals                                                       *
 * Set by the launcher class - refer to the addChildAgents() method for details. *
 *********************************************************************************/

/*****************
 * Initial plans *
 *****************/

// When processing the words 'read', the 1st plan increments the number of
// times a given word has been 'seen' by this child agent.
// If the word hasn't been seen before, the 2nd plan here is triggered, which
// adds the belief that the agent has seen the word once.
+!word_seen_checker(Word):seen::word(Word,Count)<-
    -seen::word(Word,Count);
    +seen::word(Word,Count+1);
    .
-!word_seen_checker(Word)<-
    +seen::word(Word,1);
    !new_word_seen
    .

// These two plans do the same thing as those above, but for words 'heard'.
+!word_heard_checker(Word):heard::word(Word,Count)<-
    -heard::word(Word,Count);
    +heard::word(Word,Count+1);
    .
-!word_heard_checker(Word)<-
    +heard::word(Word,1);
    !new_word_heard
    .

// The 4 plans below provide the tracking of the number of unique words seen
// and heard.
// The 'negative' plans will execute if the appropriate counter doesn't exist
// yet, and create that counter.
+!new_word_seen:seen::unique_words(Count)<-
    -seen::unique_words(Count);
    +seen::unique_words(Count+1);
    .
-!new_word_seen<-
    +seen::unique_words(1);
    .

+!new_word_heard:heard::unique_words(Count)<-
    -heard::unique_words(Count);
    +heard::unique_words(Count+1);
    .
-!new_word_heard<-
    +heard::unique_words(1);
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
        +reading(Title);
        +words_read(Title,0);
        !parse_book_text(Text);
        // Pause until the parsing is 'finished'
        .wait(finished(Title));
        // Clean up by removing the 'working' beliefs, telling the teacher
        // we're finished, then, finally, setting the 'Idle' state.
        -finished(Title);
        -words_read(Title,_);
        .send(Teacher,tell,finished(Title));
        !setState("Idle");
    }
    .

// Two plans here:
// 1st will execute once the last word has been processed and cleans up
// 2nd pulls out the 1st word in the list of words, processes it, then adds a
// new intention to process the remaining words in the list.
+!parse_book_text([])<-
    ?school::my_teacher(Teacher);
    ?reading(Title);
    -reading(Title);
    +finished(Title);
    .
+!parse_book_text([Word|Rest])<-
    !word_seen_checker(Word);
    -words_read(Title,Count);
    +words_read(Title,Count+1);
    // The double '!' here creates a separate goal/intention, rather than a
    // sub-goal, as some books will be quite loooooooooong and could cause a 
    // 'StackOverflowException' (or JaCaMo's equivalent if Java doesn't do it)
    !!parse_book_text(Rest);
    .

// The two plans here do much the same as the above two, but for words 'spoken'
// to the agent.
+!parse_speech_text([])
    .
+!parse_speech_text([Word|Rest])<-
    !word_heard_checker(Word);
    !parse_speech_text(Rest);
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
    !parse_speech_text(Utterance);
    .send(Parent,tell,finishedUtterance);
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
    ?age(Age);
    ?home::ses(Ses,_);
    ?seen::unique_words(WordsSeenCount);
    ?heard::unique_words(WordsHeardCount);
    addAnnualStats(Me,Ses,Age,WordsSeenCount,WordsHeardCount,0);
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
