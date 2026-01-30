{ include("inc/adult_common.asl") }

// Initial Beliefs
students_found(0).

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

// Time to tell all students in the 'classroom' to read a book.
// Ensures that there are books available, and that there is a 'class' of
// students before attempting to pick a book from the library & giving the
// student agents a goal of 'read_a_book(Title)'.
// The teacher then adds an intention to check the student's reading progress.
//@[atomic]
+!tell_students_to_read_book:
        my_students(Students) &
        .length(Students,Length) &
        Length>0
    <-
    .wait(sync::status("StartSchool"));
    sync::startSchool;
    get_bookTitleRandomly(Title);
    getWordCountByBookTitle(Title,WordCount);
    +current_book_word_count(WordCount);
    .send(Students,achieve,read_a_book(Title));
    for(.member(Student,Students)) {
        +sent_instruction_to_read_book(Title,Student);
//        +reading_progress(Student,Title,0);
//        !!checking_student_progress(Student,Title);
    };
    .
-!tell_students_to_read_book
    .

// Plan to ask each student to report their progress.
//+!checking_student_progress(Student,Title)<-
//    ?age_of_student(Student,Age);
//    !calculate_reading_delay(Age,Delay);
//    ?current_book_word_count(WordCount);
//    .wait(Delay*WordCount*0.1);
//    .send(Student, askOne, number_words_read(Title), WordsReadCount);
//    -reading_progress(Student,Title,_);
//    +reading_progress(Student,Title,WordsReadCount);
//    !!checking_student_progress(Student,Title);
//    .

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
//    -reading_progress(Student,Title,_);
//    .drop_intention(checking_student_progress(Student,Title));
    // Once all students have reported that they have finished reading, add an
    // intention to clean up our belief-base and tell the 'synchroniser'
    // artifact that the 'school' component has finished.
    if(NumFinished==NumStudents){
        !!finish_cleanup(Title);
    };
    .
+!finish_cleanup(Title)<-
        -current_book_word_count(_);
        ?my_students(StudentList);
        for(.member(Student,StudentList)){
            -finished(Title)[source(Student)];
        };
        sync::finishSchool;
    .
+words_read(Title,Count)[source(Student)]<-true;
//    if(checked_progress_already(Student,Title)
    .

+sync::status("StartSchool")<-
    resetGoal(tell_students_to_read_book);
    .