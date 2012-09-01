// This file is part of the Whiley-to-Java Compiler (wyjc).
//
// The Whiley-to-Java Compiler is free software; you can redistribute 
// it and/or modify it under the terms of the GNU General Public 
// License as published by the Free Software Foundation; either 
// version 3 of the License, or (at your option) any later version.
//
// The Whiley-to-Java Compiler is distributed in the hope that it 
// will be useful, but WITHOUT ANY WARRANTY; without even the 
// implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR 
// PURPOSE.  See the GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public 
// License along with the Whiley-to-Java Compiler. If not, see 
// <http://www.gnu.org/licenses/>
//
// Copyright 2010, David James Pearce. 

package wyone.spec;

import java.util.*;

public abstract class Pattern {

	public static final Any T_ANY = new Any();
	public static final Void T_VOID = new Void();
	public static final Bool T_BOOL = new Bool();
	public static final Int T_INT = new Int();
	public static final Real T_REAL = new Real();
	public static final Strung T_STRING = new Strung();
	public static final Ref<Any> T_REFANY = new Ref(T_ANY);
	public static final Compound T_COMPOUNDANY = new Compound(Compound.Kind.LIST,true,T_ANY);
	
	public static Compound T_COMPOUND(Compound.Kind kind, boolean unbounded, Collection<Pattern> elements) {
		Pattern[] es = new Pattern[elements.size()];
		int i =0;
		for(Pattern t : elements) {
			es[i++] = t;
		}
		return get(new Compound(kind,unbounded,es));
	}
	
	public static Compound T_COMPOUND(Compound.Kind kind, boolean unbounded, Pattern... elements) {
		return get(new Compound(kind,unbounded,elements));
	}
	
	public static Term T_TERM(String name, Pattern.Ref data) {
		return get(new Term(name,data));
	}
	
	public static Ref T_REF(Pattern element) {
		return get(new Ref(element));
	}
	
	public static Fun T_FUN(Pattern ret, Pattern param) {
		return get(new Fun(ret,param));
	}
	
	/**
	 * Return true if t2 is a subtype of t1 in the context of the given type
	 * hierarchy.  
	 * 
	 * @param t1
	 * @param t2
	 * @return
	 */
	public static boolean isSubtype(Pattern t1, Pattern t2) {
		if (t1 == t2 || (t2 instanceof Void) || t1 instanceof Any
				|| (t1 instanceof Real && t2 instanceof Int)) {
			return true;
		} else if (t1 instanceof Compound && t2 instanceof Compound) {
			// RULE: S-LIST
			Compound l1 = (Compound) t1;
			Compound l2 = (Compound) t2;
			Pattern[] l1_elements = l1.elements;
			Pattern[] l2_elements = l2.elements;
			if (l1_elements.length != l2_elements.length && !l1.unbounded) {
				return false;
			} else if (l2.unbounded && !l1.unbounded) {
				return false;
			} else if(l2.elements.length < l1.elements.length-1) {
				return false;
			}
			int min_len = Math.min(l1_elements.length, l2_elements.length);
			for (int i = 0; i != min_len; ++i) {
				if (!isSubtype(l1_elements[i], l2_elements[i])) {
					return false;
				}
			}
			Pattern l1_last = l1_elements[l1_elements.length-1];
			for (int i = min_len; i != l2_elements.length; ++i) {
				if (!isSubtype(l1_last,l2_elements[i])) {
					return false;
				}
			}
			return true;
		} else if (t1 instanceof Term && t2 instanceof Term) {			
			Term n1 = (Term) t1;
			Term n2 = (Term) t2;
			if(n1.name.equals(n2.name)) {
				return isSubtype(n1.data,n2.data);
			} else {				
				return false;
			}
		} else if(t1 instanceof Ref && t2 instanceof Ref) {
			Ref r1 = (Ref) t1;
			Ref r2 = (Ref) t2;
			return isSubtype(r1.element,r2.element);
		}

		return false;
	}
	
	/**
	 * Compute the <i>least upper bound</i> of two types t1 and t2. The least
	 * upper bound is a type t3 where <code>t3 :> t1</code>,
	 * <code>t3 :> t2</code> and there does not exist a type t4, where
	 * <code>t3 :> t4</code>, <code>t4 :> t1</code>, <code>t4 :> t2</code>.
	 * 
	 * @param t1
	 * @param t2
	 * @return
	 */
	public static Pattern leastUpperBound(Pattern t1, Pattern t2) {
		if (isSubtype(t1, t2)) {
			return t1;
		} else if (isSubtype(t2, t1)) {
			return t2;
		} else if (t1 instanceof Compound && t2 instanceof Compound) {
			Compound l1 = (Compound) t1;
			Compound l2 = (Compound) t2;
			// TODO: could do better here!
		} 

		// FIXME: we can do better for named types by searching the hierarchy!
		
		return T_ANY;
	}
	
	public static final class Any  extends Pattern {
		private Any() {}
		public String toString() {
			return "any";
		}
	}	
	public static final class Void  extends Pattern {
		private Void() {}
		public String toString() {
			return "void";
		}
	}	
	public static final class Bool  extends Pattern {
		private Bool() {}
		public String toString() {
			return "bool";
		}
	}
	public static final class Int  extends Pattern {
		private Int() {}
		public String toString() {
			return "int";
		}
	}
	public static final class Real  extends Pattern {
		private Real() {}
		public String toString() {
			return "real";
		}
	}
	public static final class Strung  extends Pattern {				
		private Strung() {}		
		public String toString() {
			return "string";
		}
	}
	
	public static final class Ref<T extends Pattern> extends Pattern {
		public final T element;
		
		public Ref(T element) {
			this.element = element;
		}
		
		public int hashCode() {
			return element.hashCode();
		}
		
		public boolean equals(Object o) {
			if (o instanceof Ref) {
				Ref r = (Ref) o;
				return element.equals(r.element);
			}
			return false;
		}
		
		public String toString() {
			return "^" + element;
		}
	}
	
	public static final class Term extends Pattern {		
		public final String name;
		public final Pattern.Ref data;
		
		private Term(String name, Pattern.Ref data) {			
			this.name = name;
			this.data = data;					
		}
				
		public int hashCode() {
			if(data != null) {
				return name.hashCode() + data.hashCode();
			} else {
				return name.hashCode();
			}
		}
		public boolean equals(Object o) {
			if(o instanceof Term) {
				Term t = (Term) o;
				if(data == null) {
					return t.name.equals(name) && data == t.data;
				} else {
					return t.name.equals(name) && data.equals(t.data);
				}
			}
			return false;
		}
		public String toString() {	
			if(data != null) {
				return name + "(" + data + ")";
			} else {
				return name;
			}
		}
	}
	
	public static final class Fun extends Pattern {
		public final Pattern ret;
		public final Pattern param;
		
		public Fun(Pattern ret, Pattern param) {
			this.ret = ret;
			this.param = param;
		}
		
		public int hashCode() {
			return ret.hashCode() + param.hashCode();
		}
		
		public boolean equals(Object o) {
			if (o instanceof Fun) {
				Fun r = (Fun) o;
				return ret.equals(r.ret) && param.equals(r.param);
			}
			return false;
		}
		
		public String toString() {
			return ret + "=>" + param;
		}
	}
	
	public static final class Compound extends Pattern {
		public enum Kind {
			LIST,
			SET
		}
		
		public final Kind kind;
		public final Pattern[] elements;
		public final boolean unbounded;
		
		private Compound(Kind kind, boolean unbounded, Pattern... elements) {
			this.elements = elements;
			this.kind = kind;
			this.unbounded = unbounded;
		}
		
		public Pattern element() {
			Pattern r = Pattern.T_VOID;
			for(Pattern t : elements) {
				r = Pattern.leastUpperBound(r, t);
			}
			return r;
		}
		
		public boolean equals(Object o) {
			if(o instanceof Compound) {
				Compound l = (Compound) o;
				return kind == l.kind && unbounded == l.unbounded
						&& Arrays.equals(elements, l.elements);				
			}
			return false;
		}
		public int hashCode() {
			return kind.hashCode() * Arrays.hashCode(elements);
		}
		public String toString() {
			String r = "";
			for(int i=0;i!=elements.length;++i) {
				if(i!=0) {
					r += ",";
				}
				r += elements[i];
			}
			if(unbounded) {
				r += "...";
			}
			switch(kind) {
			case LIST:
				return "[" + r + "]";
			default:
				return "{" + r + "}";			
			}			
		}
	}
		
	public static String type2str(Pattern t) {
		if(t instanceof Any) {
			return "*";
		} else if(t instanceof Void) {
			return "V";
		} else if(t instanceof Pattern.Bool) {
			return "B";
		} else if(t instanceof Pattern.Int) {
			return "I";
		} else if(t instanceof Pattern.Real) {
			return "R";
		} else if(t instanceof Pattern.Strung) {
			return "S";
		} else if(t instanceof Pattern.Ref) {
			Pattern.Ref r = (Pattern.Ref) t;
			return "^" + type2str(r.element);
		} else if(t instanceof Pattern.Fun) {
			Pattern.Fun f = (Pattern.Fun) t;
			return type2str(f.ret) + "(" + type2str(f.param) + ")";
		} else if(t instanceof Pattern.Compound) {
			Pattern.Compound st = (Pattern.Compound) t;
			String r = "";
			for(Pattern p : st.elements){
				r += type2str(p);
			}
			if(st.unbounded) {
				r += ".";
			}
			switch(st.kind) {
			case LIST:
				return "(" + r + ")";
			case SET:
				return "{" + r + "}";
			default:
				return "[" + r + "]";
			}	
		} else if(t instanceof Pattern.Term) {
			Pattern.Term st = (Pattern.Term) t;
			String r = "T" + st.name;		
			if(st.data != null) {
				return r + type2str(st.data);
			} else {
				return r;
			}
		} else {
			throw new RuntimeException("unknown type encountered: " + t);
		}
	}
	
	private static final ArrayList<Pattern> types = new ArrayList<Pattern>();
	private static final HashMap<Pattern,Integer> cache = new HashMap<Pattern,Integer>();

	private static <T extends Pattern> T get(T type) {
		Integer idx = cache.get(type);
		if(idx != null) {
			return (T) types.get(idx);
		} else {				
			cache.put(type, types.size());
			types.add(type);	
			return type;
		}
	}
}

