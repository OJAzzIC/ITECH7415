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
    // Share this household's profile settings with every member (the child
    // needs the profile names for its results row; a second parent needs
    // the pool/factor/books settings to speak correctly).
    ?home::env_profile(ParentProfile,HomeProfile);
    .send(Residents,tell,home::env_profile(ParentProfile,HomeProfile));
    ?home::pool(Pool);
    .send(Residents,tell,home::pool(Pool));
    ?home::words_factor(Factor);
    .send(Residents,tell,home::words_factor(Factor));
    ?home::books_per_day(NBooks);
    .send(Residents,tell,home::books_per_day(NBooks));
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
    ?home::pool(Pool);
    ?home::words_factor(Factor);
    ?home::books_per_day(NBooks);
    // See how many parents are in the 'home'
    .length(Parents,Length);
    // Divide the daily utterance word count by the number of parents, to
    // determine how many words this agent needs to 'speak' to the child.
    // The parent profile's daily_words_factor scales the SES budget.
    WordsToSpeak=UtteranceQty*Factor/Length;
    // Create a counter to track how many words are remaining to be 'spoken'
    +home::words_to_speak(WordsToSpeak);
    +home::iterationsLeft(20);
    while(home::iterationsLeft(Counter) & Counter>0 & home::words_to_speak(RemainingWords) & RemainingWords>0){
        DesiredWords=RemainingWords/Counter;
        getBulkUtterances(Pool,DesiredWords,Utterances,NumWordsReceived);
        .send(Child,tell,listen_to_utterances(Utterances,Counter));
        -home::words_to_speak(_);
        +home::words_to_speak(RemainingWords-NumWordsReceived);
        -home::iterationsLeft(_);
        +home::iterationsLeft(Counter-1);
    };
    // The loop may finish in fewer than 20 iterations (the word budget can
    // run out early), so work out how many batches were actually sent.
    ?home::iterationsLeft(IterationsRemaining);
    BatchesSent = 20-IterationsRemaining;
    // Remove the counters
    -home::words_to_speak(_);
    -home::iterationsLeft(_);
    // Home reading: read the configured number of randomly chosen books
    // aloud to the child.  Each book counts as one more 'batch' in the
    // day-end handshake below.
    if(NBooks>0){
        for(.range(I,1,NBooks)){
            get_bookTitleRandomly(Title);
            get_bookByTitle(Title,Text);
            .send(Child,tell,listen_to_home_book(Text));
        };
    };
    // Block (inside the synchroniser artifact) until the child has reported
    // processing every batch that was sent.  Counting inside the artifact is
    // immune to agent-side message/belief ordering; the previous design had
    // the child count batches against a fixed total of 20, which hung the
    // simulation whenever batches and the day's end arrived out of step.
    sync::awaitChildHeardAll(Child,BatchesSent+NBooks);
    // The child has definitely heard everything for today: tell it the day
    // is over (so it can go idle) and tell the synchroniser we're done.
    .send(Child,tell,day_done);
    sync::finishedHome;
    .