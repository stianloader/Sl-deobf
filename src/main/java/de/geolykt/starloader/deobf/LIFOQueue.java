package de.geolykt.starloader.deobf;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Queue;
import java.util.Spliterator;
import java.util.Stack;

/**
 * A Last-in-first-out (LIFO) queue that uses a Deque as a delegate. Technically
 * the {@link Deque} can be used directly, however the issue is that there is a
 * rather high chance of logical programmer errors as {@link Queue#add(Object)}
 * does not specify the insertion order and {@link ArrayDeque#add(Object)} adds
 * the element to the tail of the queue and not to the head as it might be
 * expected. This class resolves that issue.
 *
 * <p>
 * Alternatively the {@link Stack} class could be used as a LIFO queue, however
 * it has the drawbacks of almost every method being synchronised and thus not
 * being very good in concurrent environments as well of implementing
 * {@link Deque} and thus also inheriting the above stated issue.
 *
 * @author Geolykt
 *
 * @param <E> The type of elements that should be stored in the queue.
 */
public class LIFOQueue<E> implements Iterable<E> {

    private final Deque<E> delegate;

    public LIFOQueue(Deque<E> delegate) {
        this.delegate = delegate;
    }

    /**
     * Calls {@link Deque#addFirst(Object)}, which adds an element to the head of
     * the queue. If the underlying Dequeue has capacity restrictions, it throws an
     * {@link IllegalStateException}. Some Dequeues (such as {@link ArrayDeque}) may
     * not accept null elements and will throw a {@link NullPointerException}, which
     * would be propagated by this method.
     *
     * @param element The element to add
     */
    public void add(E element) {
        delegate.addFirst(element);
    }

    /**
     * Clears all elements from the queue.
     */
    public void clear() {
        delegate.clear();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof LIFOQueue) {
            return delegate.equals(((LIFOQueue<?>) obj).delegate);
        }
        return delegate.equals(obj);
    }

    /**
     * Obtains the dequeue delegate for more advanced operations.
     *
     * @return The delegate Deque
     */
    public Deque<E> getDelegate() {
        return delegate;
    }

    /**
     * Obtains the element at the head of the queue, if there is no such element
     * then a {@link NoSuchElementException} will be thrown. This method calls
     * {@link Deque#getFirst()}.
     *
     * @return The element at the head of the queue.
     * @throws NoSuchElementException If there are no elements in the queue.
     */
    public E getHead() {
        return delegate.getFirst();
    }

    /**
     * Obtains the amount of elements left in the queue.
     *
     * @return The amount of elements in the queue
     * @see Deque#size()
     */
    public int getSize() {
        return delegate.size();
    }

    @Override
    public int hashCode() {
        return delegate.hashCode();
    }

    /**
     * Checks whether the queue is empty. This method corresponds to
     * {@link Deque#isEmpty()}. This method should generally only return true is
     * {@link #getSize()} is equal to 0.
     *
     * @return False if there is at least one element left in the queue, otherwise
     *         true.
     */
    public boolean isEmpty() {
        return delegate.isEmpty();
    }

    @Override
    public Iterator<E> iterator() {
        return delegate.iterator();
    }

    /**
     * Calls {@link Deque#remove()} and thus throws an exception if there is no
     * element left in the queue. If there is an element left in the queue, the
     * element at the head of the queue is removed from the queue and returned.
     *
     * @return The removed element.
     * @throws NoSuchElementException If there are no elements left in the queue.
     */
    public E remove() {
        return delegate.remove();
    }

    @Override
    public Spliterator<E> spliterator() {
        return delegate.spliterator();
    }

    @Override
    public String toString() {
        return delegate.toString();
    }
}
