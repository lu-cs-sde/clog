package eval;

import java.util.Arrays;

public class Tuple {
	private long[] data;
	Tuple(long[] data) {
		this.data = Arrays.copyOf(data, data.length);
	}

	public Tuple(int arity) {
		data = new long[arity];
	}

	public int arity() {
		return data.length;
	}

	public void set(int i, long v) {
		data[i] = v;
	}

	public long get(int i) {
		return data[i];
	}

	@Override public boolean equals(Object o) {
		if (!(o instanceof Tuple))
			return false;
		Tuple other = (Tuple) o;
		if (other.arity() != arity())
			return false;
		return Arrays.equals(data, other.data);
	}

	@Override public int hashCode() {
		return Arrays.hashCode(data);
	}
}
