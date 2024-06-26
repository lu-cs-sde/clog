/* ----------------------------------------------------------------------------
 * This file was automatically generated by SWIG (http://www.swig.org).
 * Version 4.0.1
 *
 * Do not make changes to this file unless you know what you are doing--modify
 * the SWIG interface file instead.
 * ----------------------------------------------------------------------------- */

package clang.swig;

public class VectorVectorLong extends java.util.AbstractList<VectorLong> implements java.util.RandomAccess {
  private transient long swigCPtr;
  protected transient boolean swigCMemOwn;

  protected VectorVectorLong(long cPtr, boolean cMemoryOwn) {
    swigCMemOwn = cMemoryOwn;
    swigCPtr = cPtr;
  }

  protected static long getCPtr(VectorVectorLong obj) {
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
        clogJNI.delete_VectorVectorLong(swigCPtr);
      }
      swigCPtr = 0;
    }
  }

  public VectorVectorLong(VectorLong[] initialElements) {
    this();
    reserve(initialElements.length);

    for (VectorLong element : initialElements) {
      add(element);
    }
  }

  public VectorVectorLong(Iterable<VectorLong> initialElements) {
    this();
    for (VectorLong element : initialElements) {
      add(element);
    }
  }

  public VectorLong get(int index) {
    return doGet(index);
  }

  public VectorLong set(int index, VectorLong e) {
    return doSet(index, e);
  }

  public boolean add(VectorLong e) {
    modCount++;
    doAdd(e);
    return true;
  }

  public void add(int index, VectorLong e) {
    modCount++;
    doAdd(index, e);
  }

  public VectorLong remove(int index) {
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

  public VectorVectorLong() {
    this(clogJNI.new_VectorVectorLong__SWIG_0(), true);
  }

  public VectorVectorLong(VectorVectorLong other) {
    this(clogJNI.new_VectorVectorLong__SWIG_1(VectorVectorLong.getCPtr(other), other), true);
  }

  public long capacity() {
    return clogJNI.VectorVectorLong_capacity(swigCPtr, this);
  }

  public void reserve(long n) {
    clogJNI.VectorVectorLong_reserve(swigCPtr, this, n);
  }

  public boolean isEmpty() {
    return clogJNI.VectorVectorLong_isEmpty(swigCPtr, this);
  }

  public void clear() {
    clogJNI.VectorVectorLong_clear(swigCPtr, this);
  }

  public VectorVectorLong(int count, VectorLong value) {
    this(clogJNI.new_VectorVectorLong__SWIG_2(count, VectorLong.getCPtr(value), value), true);
  }

  private int doSize() {
    return clogJNI.VectorVectorLong_doSize(swigCPtr, this);
  }

  private void doAdd(VectorLong x) {
    clogJNI.VectorVectorLong_doAdd__SWIG_0(swigCPtr, this, VectorLong.getCPtr(x), x);
  }

  private void doAdd(int index, VectorLong x) {
    clogJNI.VectorVectorLong_doAdd__SWIG_1(swigCPtr, this, index, VectorLong.getCPtr(x), x);
  }

  private VectorLong doRemove(int index) {
    return new VectorLong(clogJNI.VectorVectorLong_doRemove(swigCPtr, this, index), true);
  }

  private VectorLong doGet(int index) {
    return new VectorLong(clogJNI.VectorVectorLong_doGet(swigCPtr, this, index), false);
  }

  private VectorLong doSet(int index, VectorLong val) {
    return new VectorLong(clogJNI.VectorVectorLong_doSet(swigCPtr, this, index, VectorLong.getCPtr(val), val), true);
  }

  private void doRemoveRange(int fromIndex, int toIndex) {
    clogJNI.VectorVectorLong_doRemoveRange(swigCPtr, this, fromIndex, toIndex);
  }

}
