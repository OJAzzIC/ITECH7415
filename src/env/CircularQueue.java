package vocab;

import java.util.ArrayList;
import java.util.Collection;

// Utility class which creates a circular queue of objects
public class CircularQueue<T> {
    private class Node {
        T value;
        Node next;

        Node(T e) {
            if (e != null)
                value = e;
        }
    }

    private Node front;
    private Node back;
    private int size;

    public CircularQueue() {
        size = 0;
    }

    public int getSize() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public Object[] toArray() {
        if (size == 0)
            return null;
        ArrayList<T> output = new ArrayList<>();
        Node currNode = front;
        for (int i = 0; i < size; ++i) {
            output.add(currNode.value);
            currNode = currNode.next;
        }
        return output.toArray();
    }

    public void add(T e) {
        // When adding an element, make sure the user has given us something to add
        if (e.equals(null))
            return;
        Node newNode = new Node(e);
        if (size == 0) {
            // This is the 1st node for the queue.
            front = newNode;
            front.next = front;
            back = front;
        } else {
            // There's at least one in the queue already, so add the new one to the back
            newNode.next = front;
            back.next = newNode;
            back = newNode;
        }
        ++size;
    }

    public void add(Collection<T> e) {
        // We've been given a bulk lot to insert, so process them in order...
        for (T elem : e)
            this.add(elem);
    }

    public T get() {
        // Move the 'front' and 'back' markers around.
        back = back.next;
        front = front.next;
        // The value to return to the user is now associated with the 'back' element, so
        // return that
        return back.value;
    }

    // Occasionally, it might be desirable to check what's at the front of the queue
    // without taking it off the front.
    public T peek() {
        return (front == null) ? null : front.value;
    }
}