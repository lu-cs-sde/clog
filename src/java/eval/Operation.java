package eval;

public interface Operation {
	long eval();
	String prettyPrint();

	public static Operation add(Operation l, Operation r) {
		return new Operation() {
			@Override public long eval() {
				return l.eval() + r.eval();
			}

			@Override public String prettyPrint() {
				return "(" + l.prettyPrint() + " + " + r.prettyPrint() + ")";
			}
		};
	}

	public static Operation sub(Operation l, Operation r) {
		return new Operation() {
			@Override public long eval() {
				return l.eval() - r.eval();
			}

			@Override public String prettyPrint() {
				return "(" + l.prettyPrint() + " - " + r.prettyPrint() + ")";
			}

		};
	}

	public static Operation mul(Operation l, Operation r) {
		return new Operation() {
			@Override public long eval() {
				return l.eval() * r.eval();
			}

			@Override public String prettyPrint() {
				return "(" + l.prettyPrint() + " * " + r.prettyPrint() + ")";
			}
		};
	}

	public static Operation div(Operation l, Operation r) {
		return new Operation() {
			@Override public long eval() {
				return l.eval() / r.eval();
			}

			@Override public String prettyPrint() {
				return "(" + l.prettyPrint() + " / " + r.prettyPrint() + ")";
			}
		};
	}

	public static Operation mod(Operation l, Operation r) {
		return new Operation() {
			@Override public long eval() {
				return l.eval() % r.eval();
			}

			@Override public String prettyPrint() {
				return "(" + l.prettyPrint() + " % " + r.prettyPrint() + ")";
			}

		};
	}

	public static Operation constant(long c) {
		return new Operation() {
			@Override public long eval() {
				return c;
			}

			@Override public String prettyPrint() {
				return "" + c;
			}

		};
	}

	public static Operation component(Tuple t, int i) {
		return new Operation() {
			@Override public long eval() {
				return t.get(i);
			}

			@Override public String prettyPrint() {
				return "t[" + i + "]";
			}
		};
	}

	public static Operation to_number(EvaluationContext ctx, Operation arg) {
		return new Operation() {
			@Override public long eval() {
				String s = ctx.externalizeString(arg.eval());
				return Long.parseLong(s);
			}

			@Override public String prettyPrint() {
				return "to_number(" + arg.prettyPrint() + ")";
			}
		};
	}

	// TODO: implement cat
}