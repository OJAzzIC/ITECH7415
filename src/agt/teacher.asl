{ include("inc/adult_common.asl") }

/***************************
 * Initial beliefs & goals *
 ***************************/
students_found(0).

/*****************
 * Initial plans *
 *****************/

+!find_my_students<-
    .wait(sync::status("Ready"));
    .findall(Student,play(Student,student,_),StudentList);
    .length(StudentList,NumStudents);
    +my_students(StudentList);
    -students_found(_);
    +students_found(NumStudents);
    .my_name(Me);
    for(.member(Student,StudentList)){
        .send(Student,tell,school::my_teacher(Me));
    };
    sync::schoolReady;
    .

+!read_books<-
    .wait(sync::status("StartSchool"));
    sync::startSchool;
    ?sync::agent_age(ChildAge);
    if(ChildAge<5){
        get_bookTitleRandomly(Title);
        ?my_students(Students);
        !read_book_aloud_to_child(Title,Students);
    }elif(ChildAge<=7){
        .random(Random);
        if(Random>=0.5){
            get_bookTitleRandomly(Title);
            ?my_students(Students);
            !read_book_aloud_to_child(Title,Students);
        }else{
            !tell_students_to_read_book;
        }
    }else{
        !tell_students_to_read_book;
    }
    .

+finishedListening[source(Child)]<-
    -finishedListening[source(Child)];
    +finished(Child);
    .count(finished(_),NumFinished);
    ?students_found(NumStudents);
    if(NumFinished==NumStudents){
        while(.count(finished(_),Counter) & Counter\==0){
            -finished(_);
        }
        sync::finishSchool;
    }
    .

// Time to tell all students in the 'classroom' to read a book.
// Ensures that there are books available, and that there is a 'class' of
// students before attempting to pick a book from the library & giving the
// student agents a goal of 'read_a_book(Title)'.
// The teacher then adds an intention to check the student's reading progress.
@[atomic]
+!tell_students_to_read_book:
        my_students(Students) &
        .length(Students,Length) &
        Length>0
    <-
    get_bookTitleRandomly(Title);
    getWordCountByBookTitle(Title,WordCount);
    .send(Students,achieve,read_a_book(Title));
    for(.member(Student,Students)) {
        +sent_instruction_to_read_book(Title,Student);
    };
    .
-!tell_students_to_read_book
    .

// As each student finishes reading the book, they notify the teacher.
// This plan cleans up the beliefs reading_progress & sent_instruction_to_read_book
// for the specific student reporting in.
// It then drops the intention to keep checking on the student's progress.
@[atomic]
+finished(Title)[source(Student)]<-
    .findall(Counter,finished(Title)[source(Counter)],FinishedList);
    .length(FinishedList,NumFinished);
    ?students_found(NumStudents);
    -sent_instruction_to_read_book(Title,Student);
    // Once all students have reported that they have finished reading, add an
    // intention to clean up our belief-base and tell the 'synchroniser'
    // artifact that the 'school' component has finished.
    if(NumFinished==NumStudents){
        for(.member(Student,FinishedList)){
            -finished(Title)[source(Student)];
        };
        sync::finishSchool;
    };
    .

+sync::status("StartSchool")<-
    resetGoal(read_books);
    .