package de.jgrunert.osm_routing;



@SuppressWarnings("javadoc")
public class NodeDistHeap {
    private final int nodeCount;
    protected int[] nodeHeap;
    protected int[] nodeHeapPos; // Node positions in heap
    protected int[] nodeDistBuffer;
    private int size;


	public NodeDistHeap (int nodeCount, int[] distBuffer) {
        this.nodeCount = nodeCount;
        this.nodeHeap = new int[nodeCount];  
        this.nodeHeapPos = new int[nodeCount];  
        this.nodeDistBuffer = distBuffer;
        reset();
    }
	
	private void reset() {
	    for(int i = 0; i < nodeCount; i++) {
	        nodeHeap[i] = i;
	        nodeHeapPos[i] = i;
	    }
	    size = nodeCount;
	}
	
	public void decreaseDist(int index, int newDist) {
	    if (size > 0) {
	        nodeHeapPos[nodeDistBuffer[nodeHeap[index]]] = newDist;
	        bubbleUp();
        } else {
            throw new IllegalStateException("Heap is empty");
        }
	}
	

//    public void add(int value) {
//        // grow array if needed
//        if (size >= array.length - 1) {
//            array = this.resize();
//        }        
//        
//        // place element into heap at bottom
//        size++;
//        int index = size;
//        array[index] = value;
//        
//        bubbleUp();
//    }
    
    
    public boolean isEmpty() {
        return size == 0;
    }
    
    public int getSize() {
        return size;
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

    
    public int removeMin() { 
        if (size == 0) {
            throw new IllegalStateException("Heap is empty");
        }
    
    	// what do want return?
    	int resultIndex = peekNodeIndex();
    	
    	// get rid of the last leaf/decrement
    	nodeHeap[0] = nodeHeap[size-1];
    	nodeHeap[size-1] = -1;
    	size--;
    	
    	bubbleDown();
    	
    	return resultIndex;
    }
    

    
    /**
     * Performs the "bubble down" operation to place the element that is at the 
     * root of the heap in its correct place so that the heap maintains the 
     * min-heap order property.
     */
    protected void bubbleDown() {
        int index = 0;
        
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
    
    private int getNodeDist(int index) {
        int i = nodeHeap[index];
        if(i != -1) {
            return nodeDistBuffer[i];
        } else {
            return Integer.MAX_VALUE;
        }
    }
    

    protected void bubbleUp() {
        bubbleUp(this.nodeCount);
    }
    /**
     * Performs the "bubble up" operation to place a newly inserted element 
     * (i.e. the element that is at the size index) in its correct place so 
     * that the heap maintains the min-heap order property.
     */
    protected void bubbleUp(int index) {        
        while (hasParent(index)
                && (getNodeDist(parent(index)) > getNodeDist(index))) {
            // parent/child are out of order; swap them
            swap(index, parentIndex(index));
            index = parentIndex(index);
        }        
    }
    
    
    protected boolean hasParent(int i) {
        return i > 0;
    }
    
    
    protected int leftIndex(int i) {
        return i * 2;
    }
    
    
    protected int rightIndex(int i) {
        return i * 2 + 1;
    }
    
    
    protected boolean hasLeftChild(int i) {
        return leftIndex(i) <= nodeCount;
    }
    
    
    protected boolean hasRightChild(int i) {
        return rightIndex(i) <= nodeCount;
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
        nodeHeapPos[index1] = index2;
        nodeHeapPos[index2] = index1;
    }
}