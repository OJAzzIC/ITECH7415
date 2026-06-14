{ include("inc/common.asl") }

words::unique_seen(0).
words::unique_heard(0).
words::unique_encountered(0).

+?words::word(Word,[Seen,Heard,AgeLearned])<-
    Seen=0; Heard=0; AgeLearned=0;
    +words::word(Word,[Seen,Heard,AgeLearned]);
    .

+!setState(State)<-
    -activityState(_);
    +activityState(State);
    .

+!join_home_group(HomeGroup)<-
    .wait(group(village,village,VillageGroupID));
    .wait(subgroups(Homes)[artifact_id(VillageGroupID)]);
    .wait(group(HomeGroup,indv_home,GroupID));
    home::focus(GroupID);
    home::adoptRole(offspring)[artifact_name(HomeGroup),wsp(learning_environ)];
    .

+!join_school<-
    .wait(group(Class,classroom,ClassID));
    school::focus(ClassID);
    school::adoptRole(student)[artifact_name(Class),wsp(learning_environ)];
    .

// If the household never told us its profile names (e.g. configurations
// without profile sections), fall back to the defaults.
+?home::env_profile(ParentProfile,HomeProfile)<-
    ParentProfile = "standard";
    HomeProfile = "no_books";
    .

// Record this year's statistics with the DataLogger artifact.
// Argument order MUST match DataLogger.addAnnualStats(String agentName,
// String profile, String parentProfile, String homeProfile,
// String ses, int age, int uniqueSeen, int uniqueHeard,
// int uniqueEncountered, int learned).
// 'unique_encountered' counts word types the child has been exposed to at
// least once (the paper's Table 6 exposure metric); the learned count only
// includes words that passed the learning threshold.  They are deliberately
// separate columns.
+!record_annual_stats<-
    .my_name(Me);
    ?age(Age);
    ?profile(Profile);
    ?home::env_profile(ParentProfile,HomeProfile);
    ?home::ses(Ses,_);
    ?words::unique_seen(WordsSeenCount);
    ?words::unique_heard(WordsHeardCount);
    ?words::unique_encountered(WordsEncounteredCount);
    .count(words::word(_,[_,_,SomeAge]) & SomeAge\==0,WordsLearntCount);
    addAnnualStats(Me,Profile,ParentProfile,HomeProfile,Ses,Age,WordsSeenCount,WordsHeardCount,WordsEncounteredCount,WordsLearntCount);
    .

// Signalled at the end of every year except the last.
+sync::newYear<-
    .wait(activityState("Idle"));
    !record_annual_stats;
    .

// Signalled at the end of the final year, instead of newYear.  The final
// year's stats are recorded here, before childAgentFinalised, so they are
// guaranteed to be in the DataLogger before it writes the CSV files.
// The 'not finalised' guard (with the belief added before anything else)
// makes this idempotent: a duplicated finalise signal must not record the
// stats twice or double-count this child in the synchroniser.
+sync::finalise : not finalised <-
    +finalised;
    .wait(activityState("Idle"));
    !record_annual_stats;
    .my_name(Me);
    ?home::ses(Ses,_);
    .findall(aoa(Word,Age),words::word(Word,[_,_,Age]) & Age\==0,WordAoA);
    addLearnedWords(Me,Ses,WordAoA);
    childAgentFinalised;
    .

@[atomic]
+sync::agent_age(NewAge)<-
    .wait(activityState("Idle"));
    !setState("Busy - Observed a change in the Synchroniser's state");
    ?age(Age);
    if(NewAge\==Age){
        -age(_);
        +age(NewAge);
    }
    !setState("Idle");
    .
