package de.jgrunert.osm_routing;
/**
 * CSE 373, Winter 2011, Jessica Miller
 * The BinaryHeap is an -generic- implementation of the PriorityQueue interface.  
 * This is a binary min-heap implementation of the priority queue ADT.
 */
import java.util.Arrays;

public class BinaryHeap {
    private static final int DEFAULT_CAPACITY = 10;
    protected int[] valuesArray;
    protected int[] indexArray;
    protected int size;
    
    /**
     * Constructs a new BinaryHeap.
     */
    @SuppressWarnings("unchecked")
	public BinaryHeap () {
        // Java doesn't allow construction of arrays of placeholder data types 
        valuesArray = new int[DEFAULT_CAPACITY];  
        indexArray = new int[DEFAULT_CAPACITY];  
        size = 0;
    }
    
    
    /**
     * Adds a value to the min-heap.
     */
    public void add(int value, int nodeIndex) {
        // grow array if needed
        if (size >= valuesArray.length - 1) {
            valuesArray = this.resize();
        }        
        
        // place element into heap at bottom
        size++;
        int index = size;
        valuesArray[index] = value;
        indexArray[index] = nodeIndex;
        
        bubbleUp();
    }
    
    
    /**
     * Returns true if the heap has no elements; false otherwise.
     */
    public boolean isEmpty() {
        return size == 0;
    }

    
    /**
     * Returns (but does not remove) the minimum element in the heap.
     */
    public int peekValue() {
        if (this.isEmpty()) {
            throw new IllegalStateException();
        }
        
        return valuesArray[1];
    }

    /**
     * Returns (but does not remove) the minimum element in the heap.
     */
    public int peekNodeIndex() {
        if (this.isEmpty()) {
            throw new IllegalStateException();
        }
        
        return indexArray[1];
    }
    
    
    /**
     * Removes and returns the minimum element in the heap.
     */
    public int remove() {
    	// what do want return?
        int result = peekNodeIndex();
    	
    	// get rid of the last leaf/decrement
    	valuesArray[1] = valuesArray[size];
    	valuesArray[size] = -1;
        indexArray[1] = indexArray[size];
        indexArray[size] = -1;
    	size--;
    	
    	bubbleDown();
    	
    	return result;
    }
    
    
    /**
     * Returns a String representation of BinaryHeap with values stored with 
     * heap structure and order properties.
     */
    public String toString() {
        return Arrays.toString(valuesArray);
    }

    
    /**
     * Performs the "bubble down" operation to place the element that is at the 
     * root of the heap in its correct place so that the heap maintains the 
     * min-heap order property.
     */
    protected void bubbleDown() {
        int index = 1;
        
        // bubble down
        while (hasLeftChild(index)) {
            // which of my children is smaller?
            int smallerChild = leftIndex(index);
            
            // bubble with the smaller child, if I have a smaller child
            if (hasRightChild(index)
                && valuesArray[leftIndex(index)] > valuesArray[rightIndex(index)]) {
                smallerChild = rightIndex(index);
            } 
            
            if (valuesArray[index] > valuesArray[smallerChild]) {
                swap(index, smallerChild);
            } else {
                // otherwise, get outta here!
                break;
            }
            
            // make sure to update loop counter/index of where last el is put
            index = smallerChild;
        }        
    }
    
    
    /**
     * Performs the "bubble up" operation to place a newly inserted element 
     * (i.e. the element that is at the size index) in its correct place so 
     * that the heap maintains the min-heap order property.
     */
    protected void bubbleUp() {
        int index = this.size;
        
        while (hasParent(index)
                && (parent(index) > valuesArray[index])) {
            // parent/child are out of order; swap them
            swap(index, parentIndex(index));
            index = parentIndex(index);
        }        
    }
    
    
    protected boolean hasParent(int i) {
        return i > 1;
    }
    
    
    protected int leftIndex(int i) {
        return i * 2;
    }
    
    
    protected int rightIndex(int i) {
        return i * 2 + 1;
    }
    
    
    protected boolean hasLeftChild(int i) {
        return leftIndex(i) <= size;
    }
    
    
    protected boolean hasRightChild(int i) {
        return rightIndex(i) <= size;
    }
    
    
    protected int parent(int i) {
        return valuesArray[parentIndex(i)];
    }
    
    
    protected int parentIndex(int i) {
        return i / 2;
    }
    
    
    protected int[] resize() {
        return Arrays.copyOf(valuesArray, valuesArray.length * 2);
    }
    
    
    protected void swap(int index1, int index2) {
        int tmp = valuesArray[index1];
        valuesArray[index1] = valuesArray[index2];
        valuesArray[index2] = tmp;      
        
        tmp = indexArray[index1];
        indexArray[index1] = indexArray[index2];
        indexArray[index2] = tmp;      
    }
}