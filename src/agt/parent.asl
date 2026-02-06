{include("adult_common.asl")}

/* Some 'parent' agents are told to create the group representing the
 * household.  This plan does that.
 */
+!create_home_group(Number)<-
    .my_name(Me);
    .concat("home_",Number,GroupName);
    lookupArtifact("village",HomeArtID);
    focus(HomeArtID);
    createGroup(GroupName,indv_home,MyHomeGroup);
    home::focus(MyHomeGroup);
    home::setOwner(Me);
    home::setParentGroup("village");
    home::adoptRole(parent);
    // We wait here until the system reports that the group is 'wellformed'
    // before moving on to get details about the group.
    // NOTE: If the system determines that this 'home' group is to have 2
    // 'parent' agents present, this check doesn't wait for that 2nd 'parent'
    // agent, so we handle that condition separately.
    .wait(home::formationStatus(ok)[artifact_id(MyHomeGroup)]);
    ?home::numParents(NumParents);
    !find_parents(NumParents);
    // The above sub-goal only succeeds once the group has the correct number
    // of 'parent' agents present.
    .findall(Parent,home::play(Parent,parent,_),Parents);
    .findall(Resident,home::play(Resident,_,_),Residents);
    .send(Residents,tell,home::house_parents(Parents));
    ?home::ses(Ses,Qty);
    .send(Residents,tell,home::ses(Ses,Qty));
    sync::groupCreated(GroupName);
    .

// This agent is the '2nd' parent in a home group, so they just need to join it
// once the group becomes available.
+!join_home_group(GroupName)<-
    .wait(group(village,village,HomeGroupID));
    .wait(subgroups(Homes)[artifact_id(HomeGroupID)]);
    .wait(group(GroupName,indv_home,GroupID));
    home::focus(GroupID);
    home::adoptRole(parent)[artifact_name(GroupName),wsp(learning_environ)];
    .

/* These two plans will only succeed if all required 'parent' agents have
 * joined the home group.
 * If the conditions for the 1st plan aren't met, the 2nd plan handles the
 * 'failure', by waiting ~100ms then re-adding the sub-goal to find all
 * parents again
 */
+!find_parents(NumParents):
        .findall(Parent,home::play(Parent,parent,_),Parents) &
        .length(Parents,ParentsFound) &
        ParentsFound==NumParents
        .  // Nothing to actually do here, other than to 'succeed'.
-!find_parents(NumParents)<-
    .wait(100);
    !find_parents(NumParents);
    .

// The synchroniser has signalled that it's time to start the 'home' part of
// the daily cycle.
+sync::status("StartHome")<-
    !speak_to_child;
    .

// This plan determines how many words the agent needs to 'speak' to the child
// then proceeds to pick utterances randomly from the database.
+!speak_to_child<-
    // Get the relevant environment state
    ?home::house_parents(Parents);
    ?home::play(Child,offspring,_);
    ?home::ses(_,UtteranceQty);
    // See how many parents are in the 'home'
    .length(Parents,Length);
    // Divide the daily utterance word count by the number of parents, to 
    // determine how many words this agent needs to 'speak' to the child.
    WordsToSpeak=UtteranceQty/Length;
    // Create a counter to track how many words are remaining to be 'spoken'
    +home::words_to_speak(WordsToSpeak);
    +home::iterationsLeft(20);
    while(home::iterationsLeft(Counter) & Counter>0){
        ?home::words_to_speak(RemainingWords);
        DesiredWords=RemainingWords/Counter;
        getBulkUtterances(DesiredWords,Utterances,NumWordsReceived);
        .send(Child,achieve,listen_to_speeches(Utterances,Counter));
        .wait(finishedUtterances(Counter)[source(Child)]);
        -finishedUtterances(_);
        -home::words_to_speak(_);
        +home::words_to_speak(RemainingWords-NumWordsReceived);
        -home::iterationsLeft(_);
        +home::iterationsLeft(Counter-1);
    };
    // Remove the counters
    -home::words_to_speak(_);
    -home::iterationsLeft(_);
    // Tell the synchroniser that this agent has completed the task.
    sync::finishedHome;
    .