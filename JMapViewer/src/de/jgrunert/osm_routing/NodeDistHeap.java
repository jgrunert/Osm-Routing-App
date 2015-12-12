package de.jgrunert.osm_routing;


import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Min heap for map nodes
 * @author Jonas Grunert
 *
 */
@SuppressWarnings("javadoc")
public class NodeDistHeap {
    private final int maxCapacity;
    private final float[] valuesArray;
    private final int[] gridIndexArray;
    private final short[] nodeIndexArray;
    private final Map<Integer, Map<Short, Integer>> nodeHeapIndices;
    private int size;
    private int sizeUsageMax;
    
    /**
     * Initializes heap
     */
    public NodeDistHeap (int maxCapacity) {
        this.maxCapacity = maxCapacity;
        valuesArray = new float[maxCapacity+1];  
        gridIndexArray = new int[maxCapacity+1];  
        nodeIndexArray = new short[maxCapacity+1];
        nodeHeapIndices = new HashMap<>(maxCapacity);
        size = 0;
        sizeUsageMax = 0;
    }
    
    /**
     * Resets and fills with nodes
     */
//    public void resetFill(int nodeCount) {
//        System.out.println("Start reset NodeDistHeap");
//        for(int i = 1; i <= nodeCount; i++) {
//            indexArray[i] = i-1;
//            nodeHeapIndices[i-1] = i;
//        }
//        Arrays.fill(valuesArray, Float.MAX_VALUE);
//        size = nodeCount;
//        sizeUsageMax = 0;
//        System.out.println("Finished reset NodeDistHeap");
//    }
    
    public void resetEmpty() {
        size = 0;
        sizeUsageMax = 0;
        nodeHeapIndices.clear();
    }

    public void add(int nodeGrid, short nodeGridIndex, float value) {
        if (size >= maxCapacity) {
            throw new IllegalStateException("Heap capacity exceeded");
        }        
        
        // place element into heap at bottom
        size++;
        int indexInHeap = size;
        valuesArray[indexInHeap] = value;
        gridIndexArray[indexInHeap] = nodeGrid;
        nodeIndexArray[indexInHeap] = nodeGridIndex;
        
        Map<Short, Integer> gridIndices = nodeHeapIndices.get(nodeGrid);
        if(gridIndices == null) {
            gridIndices = new HashMap<>();
            nodeHeapIndices.put(nodeGrid, gridIndices);
        }
        gridIndices.put(nodeGridIndex, indexInHeap);
        
        if(size > sizeUsageMax) {
            sizeUsageMax = size;
        }
        
        bubbleUp(this.size);
    }
    
    /**
     * Decreases key if new key smaller than existing key
     */
    public boolean decreaseKeyIfSmaller(int nodeGrid, short nodeGridIndex, float newKey) {
        int heapIndex = nodeHeapIndices.get(nodeGrid).get(nodeGridIndex);
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
    public void decreaseKey(int nodeGrid, short nodeGridIndex, int newKey) {
        int heapIndex = nodeHeapIndices.get(nodeGrid).get(nodeGridIndex);
        valuesArray[heapIndex] = newKey;
        bubbleUp(heapIndex);
    }
    
    
    public boolean isEmpty() {
        return size == 0;
    }

    
    public float peekNodeValue() {
        if (this.isEmpty()) {
            throw new IllegalStateException();
        }
        
        return valuesArray[1];
    }

    public int peekNodeGrid() {
//        if (this.isEmpty()) {
//            throw new IllegalStateException();
//        }
        
        return gridIndexArray[1];
    }
    
    public short peekNodeGridIndex() {
//        if (this.isEmpty()) {
//            throw new IllegalStateException();
//        }
        
        return nodeIndexArray[1];
    }
    


    public void removeFirst() {
//        if (this.isEmpty()) {
//          throw new IllegalStateException();
//      }
            
        // get rid of the last leaf/decrement
        valuesArray[1] = valuesArray[size];
        valuesArray[size] = -1;
        gridIndexArray[1] = gridIndexArray[size];
        gridIndexArray[size] = -1;
        nodeIndexArray[1] = nodeIndexArray[size];
        nodeIndexArray[size] = -1;
        size--;
        
        int gridIndex = gridIndexArray[1];
        Map<Short, Integer> gridIndices = nodeHeapIndices.get(gridIndex);
        gridIndices.remove(nodeIndexArray[1]);
        if(gridIndices.isEmpty()) {
            nodeHeapIndices.remove(gridIndex);
        }
        
        bubbleDown();
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

        int grid1 = gridIndexArray[index2];
        int grid2 = gridIndexArray[index1];
        gridIndexArray[index1] = grid1;
        gridIndexArray[index2] = grid2;   

        short node1 = nodeIndexArray[index2];
        short node2 = nodeIndexArray[index1];
        nodeIndexArray[index1] = node1;
        nodeIndexArray[index2] = node2;  
        
        nodeHeapIndices.get(grid1).put(node1, index1);
        nodeHeapIndices.get(grid2).put(node2, index2);
    }
}