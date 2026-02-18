// Include all beliefs, goals & plans common to all child agents
{ include("inc/child_common.asl") }

/*********************************************************************************
 * Initial beliefs & goals                                                       *
 * Set by the launcher class - refer to the addChildAgents() method for details. *
 *********************************************************************************/

/*****************
 * Initial plans *
 *****************/

// This plan incremements the number of times a given word has been 'heard'.
// Additionally, if this is the 1st time that word has been 'heard', increment
// the counter which tracks how many unique words have been heard.
+!word_heard_checker(Word)<-
    ?words::word(Word,[Seen,Heard,AgeLearned]);
    if(Heard==0){
        ?words::unique_heard(Count);
        -+words::unique_heard(Count+1);
    };
    -words::word(Word,_);
    +words::word(Word,[Seen,Heard+1,AgeLearned]);
    .

// This plan does the same as above, but for a word 'seen'.
+!word_seen_checker(Word)<-
    ?words::word(Word,[Seen,Heard,AgeLearned]);
    if(Seen==0){
        ?words::unique_seen(Count);
        -+words::unique_seen(Count+1);
    };
    -words::word(Word,_);
    +words::word(Word,[Seen+1,Heard,AgeLearned]);
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
        };
    };
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
            if(not .length(Word,0)){
                !word_seen_checker(Word);
                !try_learn_word(Word);
            };
        };
        .send(Teacher,tell,finished(Title));
        !setState("Idle");
    }
    .


// A parent agent has said an utterance to this child agent.
@[atomic]
+listen_to_speech(Utterance)[source(Parent)]<-
    !setState("Busy - Listening");
    for(.member(Word,Utterance)){
        !word_heard_checker(Word);
        !try_learn_word(Word);
    };
    .send(Parent,tell,finishedListening);
    !setState("Idle");
    .

@[atomic]
+listen_to_utterances(Utterances,Counter)[source(Parent)]<-
    !setState("Busy - Listening");
    for(.member(Utterance,Utterances)){
        for(.member(Word,Utterance)){
            !word_heard_checker(Word);
            !try_learn_word(Word);
        };
    };
    -listen_to_utterances(_,Counter)[source(Parent)];
    +finished(Counter,Parent);
    .
@[atomic]
+finished(_,Parent)<-
    .count(finished(_,_),NumFinished);
    if(NumFinished==20){
        while(.count(finished(_,_),Counter) & Counter>0){
            -finished(Counter,_);
        };
        .send(Parent,tell,finishedUtterances);
        !setState("Idle");
    }
    .
