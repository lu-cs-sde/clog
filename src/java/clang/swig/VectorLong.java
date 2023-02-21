/* ----------------------------------------------------------------------------
 * This file was automatically generated by SWIG (http://www.swig.org).
 * Version 4.0.1
 *
 * Do not make changes to this file unless you know what you are doing--modify
 * the SWIG interface file instead.
 * ----------------------------------------------------------------------------- */

package clang.swig;

public class VectorLong extends java.util.AbstractList<Integer> implements java.util.RandomAccess {
  private transient long swigCPtr;
  protected transient boolean swigCMemOwn;

  protected VectorLong(long cPtr, boolean cMemoryOwn) {
    swigCMemOwn = cMemoryOwn;
    swigCPtr = cPtr;
  }

  protected static long getCPtr(VectorLong obj) {
    return (obj == null) ? 0 : obj.swigCPtr;
  }

  @SuppressWarnings("deprecation")
  protected void finalize() {
    delete();
  }

  public synchronized void delete() {
    if (swigCPtr != 0) {
      if (swigCMemOwn) {
        swigCMemOwn = false;
        clogJNI.delete_VectorLong(swigCPtr);
      }
      swigCPtr = 0;
    }
  }

  public VectorLong(int[] initialElements) {
    this();
    reserve(initialElements.length);

    for (int element : initialElements) {
      add(element);
    }
  }

  public VectorLong(Iterable<Integer> initialElements) {
    this();
    for (int element : initialElements) {
      add(element);
    }
  }

  public Integer get(int index) {
    return doGet(index);
  }

  public Integer set(int index, Integer e) {
    return doSet(index, e);
  }

  public boolean add(Integer e) {
    modCount++;
    doAdd(e);
    return true;
  }

  public void add(int index, Integer e) {
    modCount++;
    doAdd(index, e);
  }

  public Integer remove(int index) {
    modCount++;
    return doRemove(index);
  }

  protected void removeRange(int fromIndex, int toIndex) {
    modCount++;
    doRemoveRange(fromIndex, toIndex);
  }

  public int size() {
    return doSize();
  }

  public VectorLong() {
    this(clogJNI.new_VectorLong__SWIG_0(), true);
  }

  public VectorLong(VectorLong other) {
    this(clogJNI.new_VectorLong__SWIG_1(VectorLong.getCPtr(other), other), true);
  }

  public long capacity() {
    return clogJNI.VectorLong_capacity(swigCPtr, this);
  }

  public void reserve(long n) {
    clogJNI.VectorLong_reserve(swigCPtr, this, n);
  }

  public boolean isEmpty() {
    return clogJNI.VectorLong_isEmpty(swigCPtr, this);
  }

  public void clear() {
    clogJNI.VectorLong_clear(swigCPtr, this);
  }

  public VectorLong(int count, int value) {
    this(clogJNI.new_VectorLong__SWIG_2(count, value), true);
  }

  private int doSize() {
    return clogJNI.VectorLong_doSize(swigCPtr, this);
  }

  private void doAdd(int x) {
    clogJNI.VectorLong_doAdd__SWIG_0(swigCPtr, this, x);
  }

  private void doAdd(int index, int x) {
    clogJNI.VectorLong_doAdd__SWIG_1(swigCPtr, this, index, x);
  }

  private int doRemove(int index) {
    return clogJNI.VectorLong_doRemove(swigCPtr, this, index);
  }

  private int doGet(int index) {
    return clogJNI.VectorLong_doGet(swigCPtr, this, index);
  }

  private int doSet(int index, int val) {
    return clogJNI.VectorLong_doSet(swigCPtr, this, index, val);
  }

  private void doRemoveRange(int fromIndex, int toIndex) {
    clogJNI.VectorLong_doRemoveRange(swigCPtr, this, fromIndex, toIndex);
  }

}