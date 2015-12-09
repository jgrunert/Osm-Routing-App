package de.jgrunert.osm_routing;

import java.util.Arrays;

@SuppressWarnings("javadoc")
public class NodeDistHeap {
    private final int nodeCount;
    private int[] nodeHeap;
    private int[] nodeHeapPos; // Node positions in heap
    private int[] nodeDistBuffer;
    private int size;
    
    
    /**
     * Constructs a new BinaryHeap.
     */
    public NodeDistHeap (int nodeCount) {
        this.nodeCount = nodeCount;
        this.nodeHeap = new int[nodeCount+1];  
        this.nodeHeapPos = new int[nodeCount+1];  
        this.nodeDistBuffer = new int[nodeCount+1];
        reset();
    }
    
    private void reset() {
        System.out.println("Start reset NodeDistHeap");
        for(int i = 0; i < nodeCount; i++) {
            //nodeHeap[i] = i;
            //nodeHeapPos[i] = i;
        }
        Arrays.fill(nodeDistBuffer, Integer.MAX_VALUE);
        //size = nodeCount;
        System.out.println("Finished reset NodeDistHeap");
    }
    
    
    /**
     * Adds a value to the min-heap.
     */
    public void add(int dist) {
        // place element into heap at bottom
        size++;
        int index = size;
        nodeDistBuffer[index] = dist;
        
        bubbleUp();
    }
    
    
    /**
     * Returns true if the heap has no elements; false otherwise.
     */
    public boolean isEmpty() {
        return size == 0;
    }

    
    /**
     * Returns next node index
     */
    public int peekNodeIndex() {
        if (size > 0) {
            return nodeHeap[0];
        } else {
            throw new IllegalStateException("Heap is empty");
        }
    }
    /**
     * Returns next node distance
     */
    public int peekNodeDist() {
        if (size > 0) {
            return getNodeDist(0);
        } else {
            throw new IllegalStateException("Heap is empty");
        }
    }
    
    
    
    private int getNodeDist(int index) {
        int i = nodeHeap[index];
        if(i != -1) {
            return nodeDistBuffer[i];
        } else {
            return Integer.MAX_VALUE;
        }
    }
    

    
    /**
     * Removes and returns the minimum element in the heap.
     */
    public int remove() {
        // what do want return?
        int result = peekNodeIndex();
        
        // get rid of the last leaf/decrement
        nodeHeap[1] = nodeHeap[size];
        nodeHeap[size] = -1;
        size--;
        
        bubbleDown();
        
        return result;
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
                && getNodeDist(leftIndex(index)) > getNodeDist(rightIndex(index))) {
                smallerChild = rightIndex(index);
            } 
            
            if (getNodeDist(index) > getNodeDist(smallerChild)) {
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
                && (getNodeDist(parent(index)) > (getNodeDist(index)))) {
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
        return nodeHeap[parentIndex(i)];
    }
    
    
    protected int parentIndex(int i) {
        return i / 2;
    }
    
    
    protected void swap(int index1, int index2) {
        int tmp = nodeHeap[index1];
        nodeHeap[index1] = nodeHeap[index2];
        nodeHeap[index2] = tmp;        
    }
}