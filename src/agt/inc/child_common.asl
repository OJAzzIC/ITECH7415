// This file contains starting beliefs and plans which are common to all child
// agents.

// Include all beliefs, goals & plans common to all agents
{ include("inc/common.asl") }

words::unique_seen(0).
words::unique_heard(0).

// In the above 3 plans, a check is made to see if a word is present in the
// belief base.  The 1st time that check is made for a given word, this plan
// will be executed (as the word isn't in the belief base).
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
    .count(words::word(_,[_,_,SomeAge]) & SomeAge\==0,WordsLearntCount);
    addAnnualStats(Me,Ses,Age,WordsSeenCount,WordsHeardCount,WordsLearntCount);
    .

// A little back-and-forth plan to get the synchroniser to wait until all child
// agents have finished everything.
@[atomic]
+sync::finalise<-
    .wait(activityState("Idle"));
    .my_name(Me);
    ?home::ses(Ses,_);
    .findall(aoa(Word,Age),words::word(Word,[_,_,Age]) & Age\==0,WordAoA);
    addLearnedWords(Me,Ses,WordAoA);
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
