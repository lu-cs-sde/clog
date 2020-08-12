package eval;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;

public class Relation2 {
	int arity = 0;
	String name = "AnonRel";

	private Map<Index, SortedSet<Tuple>> indexedMaps = new HashMap<>();
	private SortedSet<Tuple> currentSet = null;
	private Index defaultIndex;

	public Relation2(int arity, String name) {
		this(arity);
		this.name = name;
	}

	@Override public String toString() {
		return "name(" + arity + ")";
	}

	public String getName() {
		return name;
	}

	public Relation2(int arity) {
		this.arity = arity;

		// create a default index consisting of all columns
		List<Integer> defaultIndices = new ArrayList<>();
		for (int i = 0; i < arity; ++i)
			defaultIndices.add(i);

		defaultIndex = new Index(defaultIndices, arity);
		setIndex(defaultIndex);
	}

	public void setIndex(Index index) {
		currentSet = indexedMaps.get(index);
		if (currentSet == null) {
			// no set for the requested index, add new one
			currentSet = new TreeSet<>(index);
			indexedMaps.put(index, currentSet);

			// initialize the current set with all the values
			for (SortedSet<Tuple> s : indexedMaps.values()) {
				currentSet.addAll(s);
				break;
			}
		}
	}

	public SortedSet<Tuple> lookup(long[] prefix) {
		assert prefix.length <= arity;
		assert currentSet != null;

		Tuple keyL = new Tuple(arity);
		Tuple keyH = new Tuple(arity);

		for (int i = 0; i < prefix.length; ++i) {
			keyL.set(i, prefix[i]);
			keyH.set(i, prefix[i]);
		}

		for (int i = prefix.length; i < arity; ++i) {
			keyL.set(i, Long.MIN_VALUE);
			keyH.set(i, Long.MAX_VALUE);
		}

		return currentSet.subSet(keyL, keyH);
	}

	public void insert(Tuple t) {
		for (SortedSet<Tuple> s : indexedMaps.values())
			s.add(t);
	}

	public void insert(Collection<? extends Tuple> ts) {
		for (SortedSet<Tuple> s : indexedMaps.values())
			s.addAll(ts);
	}

	public int arity() {
		return arity;
	}

	public int size() {
		return indexedMaps.get(defaultIndex).size();
	}
}
