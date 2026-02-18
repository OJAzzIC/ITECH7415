{ include("inc/common.asl") }

+!read_book_aloud_to_child(Title,Child)<-
    get_bookByTitle(Title,Text);
    .send(Child,tell,listen_to_speech(Text));
    .
