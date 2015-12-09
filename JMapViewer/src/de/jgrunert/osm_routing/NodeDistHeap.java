package de.jgrunert.osm_routing;


import java.util.Arrays;

/**
 * Min heap for map nodes
 * @author Jonas Grunert
 *
 */
@SuppressWarnings("javadoc")
public class NodeDistHeap {
    protected int[] valuesArray;
    protected int[] indexArray;
    protected int[] nodeHeapIndices;
    protected int size;
    
    /**
     * Initializes heap
     */
    public NodeDistHeap (int nodeCount) {
        valuesArray = new int[nodeCount+1];  
        indexArray = new int[nodeCount+1];  
        nodeHeapIndices = new int[nodeCount]; 
        size = 0;
    }
    
    
    public void add(int value, int nodeIndex) {
        if (size >= valuesArray.length - 1) {
            throw new IllegalStateException("Heap capacity exceeded");
        }        
        
        // place element into heap at bottom
        size++;
        int index = size;
        valuesArray[index] = value;
        indexArray[index] = nodeIndex;
        nodeHeapIndices[nodeIndex] = index;
        
        bubbleUp(this.size);
    }
    
    public void decreaseKey(int index, int newKey) {
        int heapIndex = nodeHeapIndices[index];
        valuesArray[heapIndex] = newKey;
        bubbleUp(heapIndex);
    }
    
    
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
    
    
    protected void bubbleDown() 
    {
        
        int i = 1;
        
        // bubble down
        while (hasLeftChild(i)) {
            // which of my children is smaller?
            int smallerChild = leftIndex(i);
            
            // bubble with the smaller child, if I have a smaller child
            if (hasRightChild(i)
                && valuesArray[leftIndex(i)] > valuesArray[rightIndex(i)]) {
                smallerChild = rightIndex(i);
            } 
            
            if (valuesArray[i] > valuesArray[smallerChild]) {
                swap(i, smallerChild);
            } else {
                // otherwise, get outta here!
                break;
            }
            
            // make sure to update loop counter/index of where last el is put
            i = smallerChild;
        }        
    }
    
    
    protected void bubbleUp(int i) {
        while (hasParent(i)
                && (parent(i) > valuesArray[i])) {
            // parent/child are out of order; swap them
            swap(i, parentIndex(i));
            i = parentIndex(i);
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


    
    protected void swap(int index1, int index2) {
        int tmp = valuesArray[index1];
        valuesArray[index1] = valuesArray[index2];
        valuesArray[index2] = tmp;      
        
        tmp = indexArray[index1];
        indexArray[index1] = indexArray[index2];
        indexArray[index2] = tmp;   
        
        nodeHeapIndices[indexArray[index1]] = index1;
        nodeHeapIndices[indexArray[index2]] = index2;
    }
}