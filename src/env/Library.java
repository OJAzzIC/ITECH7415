package vocab;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;
import java.util.concurrent.locks.ReentrantLock;

import cartago.*;
import jason.asSyntax.*;
import jason.stdlib.foreach;

public class Library extends Artifact {
    private static Random rnd = new Random();
    private static File libraryDirectory;
    private static HashMap<String, String> books = new HashMap<>();
    private static final ReentrantLock loadingLock = new ReentrantLock();

    // By the time init() is called, loadBookTitles() has done its job,
    // therefore the 'books' HashMap has been populated, so this works.
    void init() {
        defineObsProperty("book_count", books.size());
    }

    private void loadBook(String filename) {
        try {
            File f = Paths.get(libraryDirectory.getAbsolutePath(), filename).toFile();
            if (f.isFile()) {
                byte[] byteContents = Files.readAllBytes(f.toPath());
                String contents;
                ByteBuffer buffer = ByteBuffer.wrap(byteContents);
                if (buffer.remaining() >= 3) {
                    byte b0 = buffer.get();
                    byte b1 = buffer.get();
                    byte b2 = buffer.get();
                    if (((b0 & 0xFF) == 0xEF) && ((b1 & 0xFF) == 0xBB) && ((b2 & 0xFF) == 0xBF))
                        contents = StandardCharsets.UTF_8.decode(buffer).toString();
                    else {
                        buffer.position(0);
                        contents = StandardCharsets.UTF_8.decode(buffer).toString();
                    }
                    books.put(f.getName(), contents);
                }
            }
        } catch (IOException ex) {
        }

    }

    // Attempt to find all the .txt files in the given path and store their
    // filenames into the books HashMap.
    public static boolean loadBookTitles(String path) {
        // If some books are already loaded, don't bother trying to load more, just
        // return 'false'.
        if (books.size() > 0)
            return false;
        // Try to read the filenames of files in the directory, and only store the name
        // of any '.txt' files found...
        libraryDirectory = new File(path);
        System.out.printf("Attempting to load books from: %s\n", libraryDirectory.getAbsolutePath());
        String filename;
        final FileFilter filter = new FileFilter() {
            public boolean accept(File file) {
                return (file.getName().endsWith(".txt"));
            }
        };
        for (File f : libraryDirectory.listFiles(filter)) {
            books.put(f.getName(), "");
        }
        return books.size() > 0;
    }

    @OPERATION
    void getSize(OpFeedbackParam<Integer> result) {
        result.set(books.size());
    }

    // An agent wants the title of a randomly selected book
    @OPERATION
    void get_bookTitleRandomly(OpFeedbackParam<String> result) {
        result.set((String) books.keySet().toArray()[rnd.nextInt(books.size())]);
    }

    // An agent wants the text of a book, by providing the title of the book
    @OPERATION
    void get_bookByTitle(String title, OpFeedbackParam<String[]> result) {
        // Ensure the book's title is in the library already, otherwise give back an
        // empty String[].
        if (!books.containsKey(title)) {
            result.set(new String[] {});
            return;
        }
        // Ensure that no other loading operation is happening.
        // lock() will block until this thread is able to get the lock.
        loadingLock.lock();
        // Got the lock.
        // If the title hasn't been loaded yet, .isEmpty() will be true, so load the
        // book from the file.
        if (books.getOrDefault(title, "").isEmpty()) {
            loadBook(title);
        }
        // Release the lock so someone else can use it.
        loadingLock.unlock();
        // Return the text of the book.
        result.set(books.get(title).split(" "));
    }

    // An agent wants to know how many words are in a book
    @OPERATION
    void getWordCountByBookTitle(String title, OpFeedbackParam<Integer> result) {
        // Ensure the book's title is in the library already, otherwise give back an
        // empty String[].
        if (!books.containsKey(title)) {
            result.set(-1);
            return;
        }
        // Ensure that no other loading operation is happening.
        // lock() will block until this thread is able to get the lock.
        loadingLock.lock();
        // Got the lock.
        // If the title hasn't been loaded yet, .isEmpty() will be true, so load the
        // book from the file.
        if (books.getOrDefault(title, "").isEmpty()) {
            loadBook(title);
        }
        // Release the lock so someone else can use it.
        loadingLock.unlock();
        // Split the text, using ' ' as the delimiter, then return the number of words
        // in the String[]
        result.set(books.get(title).split(" ").length);
    }
}