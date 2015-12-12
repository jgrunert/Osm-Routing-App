package de.jgrunert.osm_routing;


import java.util.Arrays;

/**
 * Min heap for map nodes
 * @author Jonas Grunert
 *
 */
@SuppressWarnings("javadoc")
public class NodeDistHeap {
    private final int maxSize;
    private final float[] valuesArray;
    private final int[] indexArray;
    private final int[] nodeHeapIndices;
    private int size;
    private int sizeUsageMax;
    
    /**
     * Initializes heap
     */
    public NodeDistHeap (int maxSize) {
        this.maxSize = maxSize;
        valuesArray = new float[maxSize+1];  
        indexArray = new int[maxSize+1];  
        nodeHeapIndices = new int[maxSize]; 
        //size = 0;
    }
    
    /**
     * Resets and fills with nodes
     */
    public void resetFill(int nodeCount) {
        System.out.println("Start reset NodeDistHeap");
        for(int i = 1; i <= nodeCount; i++) {
            indexArray[i] = i-1;
            nodeHeapIndices[i-1] = i;
        }
        Arrays.fill(valuesArray, Float.MAX_VALUE);
        size = nodeCount;
        sizeUsageMax = 0;
        System.out.println("Finished reset NodeDistHeap");
    }
    
    public void resetEmpty() {
        size = 0;
        sizeUsageMax = 0;
    }

    public void add(int nodeIndex, float value) {
        if (size >= maxSize) {
            throw new IllegalStateException("Heap capacity exceeded");
        }        
        
        // place element into heap at bottom
        size++;
        int index = size;
        valuesArray[index] = value;
        indexArray[index] = nodeIndex;
        nodeHeapIndices[nodeIndex] = index;
        
        if(size > sizeUsageMax) {
            sizeUsageMax = size;
        }
        
        bubbleUp(this.size);
    }
    
    /**
     * Decreases key if new key smaller than existing key
     */
    public boolean decreaseKeyIfSmaller(int index, float newKey) {
        int heapIndex = nodeHeapIndices[index];
        if (newKey < valuesArray[heapIndex]) {
            valuesArray[heapIndex] = newKey;
            bubbleUp(heapIndex);
            return true;
        }
        else {
            return false;
        }
    }

    /**
     * Changes key, assumes that newKey<oldKey
     */
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
    public float peekNodeValue() {
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
    
    
    protected float parent(int i) {
        return valuesArray[parentIndex(i)];
    }
    
    
    protected int parentIndex(int i) {
        return i / 2;
    }
    
    public int getSize() {
        return size;
    }
    
    public int getSizeUsageMax() {
        return sizeUsageMax;
    }


    
    protected void swap(int index1, int index2) {
        float tmp1 = valuesArray[index1];
        valuesArray[index1] = valuesArray[index2];
        valuesArray[index2] = tmp1;      
        
        int tmp2 = indexArray[index1];
        indexArray[index1] = indexArray[index2];
        indexArray[index2] = tmp2;   
        
        nodeHeapIndices[indexArray[index1]] = index1;
        nodeHeapIndices[indexArray[index2]] = index2;
    }
}