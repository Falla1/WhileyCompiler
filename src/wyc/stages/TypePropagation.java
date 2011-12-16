// Copyright (c) 2011, David J. Pearce (djp@ecs.vuw.ac.nz)
// All rights reserved.
//
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions are met:
//    * Redistributions of source code must retain the above copyright
//      notice, this list of conditions and the following disclaimer.
//    * Redistributions in binary form must reproduce the above copyright
//      notice, this list of conditions and the following disclaimer in the
//      documentation and/or other materials provided with the distribution.
//    * Neither the name of the <organization> nor the
//      names of its contributors may be used to endorse or promote products
//      derived from this software without specific prior written permission.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
// ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
// WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
// DISCLAIMED. IN NO EVENT SHALL DAVID J. PEARCE BE LIABLE FOR ANY
// DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
// (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
// LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
// ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
// (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
// SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

package wyc.stages;

import static wyil.util.SyntaxError.*;
import static wyil.util.ErrorMessages.*;

import java.util.*;

import wyc.TypeExpander;
import wyc.lang.*;
import wyc.lang.WhileyFile.*;
import wyil.ModuleLoader;
import wyil.lang.Import;
import wyil.lang.PkgID;
import wyil.lang.Type;
import wyil.lang.Code.OpDir;
import wyil.util.Pair;
import wyil.util.ResolveError;
import wyil.util.SyntacticElement;
import wyil.util.SyntaxError;
import static wyil.util.SyntaxError.*;

/**
 * Propagates type information in a flow-sensitive fashion from declared
 * parameter and return types through assigned expressions, to determine types
 * for all intermediate expressions and variables. For example:
 * 
 * <pre>
 * int sum([int] data):
 *     r = 0          // infers int type for r, based on type of constant
 *     for v in data: // infers int type for v, based on type of data
 *         r = r + v  // infers int type for r, based on type of operands 
 *     return r       // infers int type for r, based on type of r after loop
 * </pre>
 * 
 * The flash points here are the variables <code>r</code> and <code>v</code> as
 * <i>they do not have declared types</i>. Type propagation is responsible for
 * determing their type.
 * 
 * Loops present an interesting challenge for type propagation. Consider this
 * example:
 * 
 * <pre>
 * real loopy(int max):
 *     i = 0
 *     while i < max:
 *         i = i + 0.5
 *     return i
 * </pre>
 * 
 * On the first pass through the loop, variable <code>i</code> is inferred to
 * have type <code>int</code> (based on the type of the constant <code>0</code>
 * ). However, the add expression is inferred to have type <code>real</code>
 * (based on the type of the rhs) and, hence, the resulting type inferred for
 * <code>i</code> is <code>real</code>. At this point, the loop must be
 * reconsidered taking into account this updated type for <code>i</code>.
 * 
 * In some cases, this process must update the underlying expressions to reflect
 * the correct operator. For example:
 * 
 * <pre>
 * {int} f({int} x, {int} y):
 *    return x+y
 * </pre>
 * 
 * Initially, the expression <code>x+y</code> is assumed to be arithmetic
 * addition. During type propagation, however, it becomes apparent that its
 * operands are both sets. Therefore, the underlying AST node is updated to
 * represent a set union.
 * 
 * <h3>References</h3>
 * <ul>
 * <li>
 * <p>
 * David J. Pearce and James Noble. Structural and Flow-Sensitive Types for
 * Whiley. Technical Report, Victoria University of Wellington, 2010.
 * </p>
 * </li>
 * </ul>
 * 
 * @author David J. Pearce
 * 
 */
public final class TypePropagation {
	private final ModuleLoader loader;
	private final TypeExpander expander;
	private ArrayList<Scope> scopes = new ArrayList<Scope>();
	private String filename;
	private WhileyFile.FunDecl method;
	
	public TypePropagation(ModuleLoader loader, TypeExpander expander) {
		this.loader = loader;
		this.expander = expander;
	}
	
	public void propagate(WhileyFile wf) {
		this.filename = wf.filename;
		
		for(WhileyFile.Decl decl : wf.declarations) {
			if(decl instanceof FunDecl) {
				propagate((FunDecl)decl);
			} else if(decl instanceof TypeDecl) {
				propagate((TypeDecl)decl);					
			} else if(decl instanceof ConstDecl) {
				propagate((ConstDecl)decl);					
			}			
		}
	}
	
	public void propagate(ConstDecl cd) {
		
	}
	
	public void propagate(TypeDecl td) {		
		if(td.constraint != null) {			
			Env environment = new Env();
			environment.put("$", td.type);
			propagate(td.constraint,environment);
		}
	}

	public void propagate(FunDecl fd) {
		this.method = fd;
		Env environment = new Env();
		Type.Function type = fd.type;
		ArrayList<Type> paramTypes = type.params();
		int i=0;
		for (WhileyFile.Parameter p : fd.parameters) {						
			environment = environment.put(p.name,paramTypes.get(i++));
		}
		
		if(fd instanceof MethDecl) {
			MethDecl md = (MethDecl) fd;			
			Type.Method mt = (Type.Method) type; 
			environment = environment.put("this",mt.receiver());
		}
		
		if(fd.precondition != null) {
			propagate(fd.precondition,environment.copy());
		}
		
		if(fd.postcondition != null) {			
			environment = environment.put("$", type.ret());
			propagate(fd.postcondition,environment.copy());
			// The following is a little sneaky and helps to avoid unnecessary
			// copying of environments. 
			environment = environment.remove("$");
		}
				
		propagate(fd.statements,environment);
	}
	
	private Env propagate(ArrayList<Stmt> body, Env environment) {
		for (Stmt stmt : body) {
			environment = propagate(stmt, environment);
		}
		return environment;
	}
	
	private Env propagate(Stmt stmt,
			Env environment) {
		try {
			if(stmt instanceof Stmt.Assign) {
				return propagate((Stmt.Assign) stmt,environment);
			} else if(stmt instanceof Stmt.Return) {
				return propagate((Stmt.Return) stmt,environment);
			} else if(stmt instanceof Stmt.IfElse) {
				return propagate((Stmt.IfElse) stmt,environment);
			} else if(stmt instanceof Stmt.While) {
				return propagate((Stmt.While) stmt,environment);
			} else if(stmt instanceof Stmt.For) {
				return propagate((Stmt.For) stmt,environment);
			} else if(stmt instanceof Stmt.Switch) {
				return propagate((Stmt.Switch) stmt,environment);
			} else if(stmt instanceof Expr.Invoke) {
				propagate((Expr.Invoke) stmt,environment);
				return environment;
			} else if(stmt instanceof Stmt.DoWhile) {
				return propagate((Stmt.DoWhile) stmt,environment);
			} else if(stmt instanceof Stmt.Break) {
				return propagate((Stmt.Break) stmt,environment);
			} else if(stmt instanceof Stmt.Throw) {
				return propagate((Stmt.Throw) stmt,environment);
			} else if(stmt instanceof Stmt.TryCatch) {
				return propagate((Stmt.TryCatch) stmt,environment);
			} else if(stmt instanceof Stmt.Assert) {
				return propagate((Stmt.Assert) stmt,environment);
			} else if(stmt instanceof Stmt.Debug) {
				return propagate((Stmt.Debug) stmt,environment);
			} else if(stmt instanceof Stmt.Skip) {
				return propagate((Stmt.Skip) stmt,environment);
			} else {
				internalFailure("unknown statement encountered",filename,stmt);
				return null; // deadcode
			}
		} catch(SyntaxError e) {
			throw e;
		} catch(Throwable e) {
			internalFailure(e.getMessage(),filename,stmt,e);
			return null; // dead code
		}
	}
	
	private Env propagate(Stmt.Assert stmt,
			Env environment) {
		return environment;
	}
	
	private Env propagate(Stmt.Assign stmt,
			Env environment) {
		return environment;
	}
	
	private Env propagate(Stmt.Break stmt,
			Env environment) {
		return environment;
	}
	
	private Env propagate(Stmt.Debug stmt,
			Env environment) {
		return environment;
	}
	
	private Env propagate(Stmt.DoWhile stmt,
			Env environment) {
		return environment;
	}
	
	private Env propagate(Stmt.For stmt,
			Env environment) {
		return environment;
	}
	
	private Env propagate(Stmt.IfElse stmt,
			Env environment) {
		return environment;
	}
	
	private Env propagate(Stmt.Return stmt, Env environment) {
		if (stmt.expr != null) {
			stmt.expr = propagate(stmt.expr, environment);
			checkIsSubtype(method.type.ret(), stmt.expr.type(), stmt.expr);
		}
		return BOTTOM;
	}
	
	private Env propagate(Stmt.Skip stmt,
			Env environment) {
		return environment;
	}
	
	private Env propagate(Stmt.Switch stmt,
			Env environment) {
		return environment;
	}
	
	private Env propagate(Stmt.Throw stmt,
			Env environment) {
		return environment;
	}
	
	private Env propagate(Stmt.TryCatch stmt,
			Env environment) {
		return environment;
	}
	
	private Env propagate(Stmt.While stmt,
			Env environment) {
		return environment;
	}
	
	private Expr propagate(Expr expr,
			Env environment) {
		Type type;
		
		try {
			if(expr instanceof Expr.BinOp) {
				return propagate((Expr.BinOp) expr,environment); 
			} else if(expr instanceof Expr.Comprehension) {
				return propagate((Expr.Comprehension) expr,environment); 
			} else if(expr instanceof Expr.Constant) {
				return propagate((Expr.Constant) expr,environment); 
			} else if(expr instanceof Expr.Convert) {
				return propagate((Expr.Convert) expr,environment); 
			} else if(expr instanceof Expr.DictionaryGen) {
				return propagate((Expr.DictionaryGen) expr,environment); 
			} else if(expr instanceof Expr.ExternalAccess) {
				return propagate((Expr.ExternalAccess) expr,environment); 
			} else if(expr instanceof Expr.Function) {
				return propagate((Expr.Function) expr,environment); 
			} else if(expr instanceof Expr.Invoke) {
				return propagate((Expr.Invoke) expr,environment); 
			} else if(expr instanceof Expr.Access) {
				return propagate((Expr.Access) expr,environment); 
			} else if(expr instanceof Expr.LocalVariable) {
				return propagate((Expr.LocalVariable) expr,environment); 
			} else if(expr instanceof Expr.ModuleAccess) {
				return propagate((Expr.ModuleAccess) expr,environment); 
			} else if(expr instanceof Expr.NaryOp) {
				return propagate((Expr.NaryOp) expr,environment); 
			} else if(expr instanceof Expr.PackageAccess) {
				return propagate((Expr.PackageAccess) expr,environment); 
			} else if(expr instanceof Expr.RecordAccess) {
				return propagate((Expr.RecordAccess) expr,environment); 
			} else if(expr instanceof Expr.RecordGen) {
				return propagate((Expr.RecordGen) expr,environment); 
			} else if(expr instanceof Expr.Spawn) {
				return propagate((Expr.Spawn) expr,environment); 
			} else if(expr instanceof Expr.TupleGen) {
				return  propagate((Expr.TupleGen) expr,environment); 
			} else if(expr instanceof Expr.TypeVal) {
				return propagate((Expr.TypeVal) expr,environment); 
			} else {
				internalFailure("unknown expression encountered (" + expr.getClass().getName() +")",filename,expr);
				return null; // dead code
			}
		} catch(SyntaxError e) {
			throw e;
		} catch(Throwable e) {
			internalFailure(e.getMessage(),filename,expr,e);
			return null; // dead code
		}		
	}
	
	private Expr propagate(Expr.BinOp expr,
			Env environment) {
		expr.lhs = propagate(expr.lhs,environment);
		expr.rhs = propagate(expr.rhs,environment);
		Type lhs = expr.lhs.type();
		Type rhs = expr.rhs.type();
	
		boolean lhs_set = Type.isSubtype(Type.Set(Type.T_ANY, false),lhs);
		boolean rhs_set = Type.isSubtype(Type.Set(Type.T_ANY, false),rhs);		
		boolean lhs_list = Type.isSubtype(Type.List(Type.T_ANY, false),lhs);
		boolean rhs_list = Type.isSubtype(Type.List(Type.T_ANY, false),rhs);
		boolean lhs_str = Type.isSubtype(Type.T_STRING,lhs);
		boolean rhs_str = Type.isSubtype(Type.T_STRING,rhs);
		
		Type result;
		if(lhs_str || rhs_str) {						
			switch(expr.op) {				
				case ADD:			
					expr.op = Expr.BOp.STRINGAPPEND;				
					break;
				default:
					syntaxError("Invalid string operation: " + expr.op,filename,expr);					
			}
			
			result = Type.T_STRING;
		} else if(lhs_set && rhs_set) {		
			Type.Set type = Type.effectiveSetType(Type.Union(lhs,rhs));
			
			switch(expr.op) {				
				case ADD:																				
					expr.op = Expr.BOp.LISTAPPEND;
					break;
				default:
					syntaxError("Invalid list operation: " + expr.op,filename,expr);		
			}
			
			result = type;
		} else if(lhs_list && rhs_list) {
			Type.List type = Type.effectiveListType(Type.Union(lhs,rhs));
			
			switch(expr.op) {				
				case ADD:																				
					expr.op = Expr.BOp.LISTAPPEND;
					break;
				default:
					syntaxError("Invalid set operation: " + expr.op,filename,expr);		
			}
			
			result = type;			
		} else {			
			switch(expr.op) {
			case BITWISEAND:
			case BITWISEOR:
			case BITWISEXOR:
				checkIsSubtype(Type.T_BYTE,lhs,expr);
				checkIsSubtype(Type.T_BYTE,rhs,expr);
				result = Type.T_BYTE;
			case LEFTSHIFT:
			case RIGHTSHIFT:
				checkIsSubtype(Type.T_BYTE,lhs,expr);
				checkIsSubtype(Type.T_INT,rhs,expr);
				result = Type.T_BYTE;
			case RANGE:
				checkIsSubtype(Type.T_INT,lhs,expr);
				checkIsSubtype(Type.T_INT,rhs,expr);
				result = Type.List(Type.T_INT, false);
			case REM:
				checkIsSubtype(Type.T_INT,lhs,expr);
				checkIsSubtype(Type.T_INT,rhs,expr);
				result = Type.T_INT;
			default:
				// all other operations go through here
				if(Type.isImplicitCoerciveSubtype(lhs,rhs)) {
					checkIsSubtype(Type.T_REAL,lhs,expr);
					if(Type.isSubtype(Type.T_CHAR, lhs)) {
						result = Type.T_CHAR;
					} else if(Type.isSubtype(Type.T_INT, lhs)) {
						result = Type.T_INT;
					} else {
						result = Type.T_REAL;
					}				
				} else {
					checkIsSubtype(Type.T_REAL,lhs,expr);
					checkIsSubtype(Type.T_REAL,rhs,expr);				
					if(Type.isSubtype(Type.T_CHAR, rhs)) {
						result = Type.T_CHAR;
					} else if(Type.isSubtype(Type.T_INT, rhs)) {
						result = Type.T_INT;
					} else {
						result = Type.T_REAL;
					}
				} 			
			}
		}	
		
		expr.type = result;
		return expr;
	}
	
	private Expr propagate(Expr.Comprehension expr,
			Env environment) {
		return expr;
	}
	
	private Expr propagate(Expr.Constant expr,
			Env environment) {
		return expr;
	}

	private Expr propagate(Expr.Convert c,
			Env environment) {
		c.expr = propagate(c.expr,environment);
		Type from = c.expr.type();
		Type to = c.type();
		if (!Type.isExplicitCoerciveSubtype(to, from)) {			
			syntaxError(errorMessage(SUBTYPE_ERROR, to, from), filename, c);
		}	
		return c;
	}
	
	private Expr propagate(Expr.DictionaryGen expr,
			Env environment) {
		return null;
	}
	
	private Expr propagate(Expr.ExternalAccess expr,
			Env environment) {
		return null;
	}
	
	private Expr propagate(Expr.Function expr,
			Env environment) {
		return null;
	}
	
	private Expr propagate(Expr.Invoke expr,
			Env environment) {
		return null;
	}	
	
	private Expr propagate(Expr.Access expr,
			Env environment) {			
		expr.src = propagate(expr.src,environment);
		expr.index = propagate(expr.index,environment);		
		Type idx = expr.index.type();
		Type src = expr.src.type();
		Type result;
		if(Type.isImplicitCoerciveSubtype(Type.Dictionary(Type.T_ANY, Type.T_ANY),src)) {			
			// this indicates a dictionary access, rather than a list access			
			Type.Dictionary dict = Type.effectiveDictionaryType(src);			
			if(dict == null) {
				syntaxError(errorMessage(INVALID_DICTIONARY_EXPRESSION),filename,expr);
			}
			checkIsSubtype(dict.key(),idx,expr);
			expr.op = Expr.AOp.DICT_ACCESS;
			// OK, it's a hit			
			result = dict;
		} else if(Type.isImplicitCoerciveSubtype(Type.T_STRING,src)) {
			checkIsSubtype(Type.T_INT,idx,expr);			
			expr.op = Expr.AOp.STRING_ACCESS;			
			result = Type.T_STRING;
		} else {		
			Type.List list = Type.effectiveListType(src);			
			if(list == null) {
				syntaxError(errorMessage(INVALID_LIST_EXPRESSION),filename,expr);				
			}			
			checkIsSubtype(Type.T_INT,idx,expr);
			expr.op = Expr.AOp.LIST_ACCESS;
			result =  list;			
		}
		
		expr.type = result;
		return expr;
	}
	
	private Expr propagate(Expr.LocalVariable expr,
			Env environment) {
		return null;
	}
	
	private Expr propagate(Expr.ModuleAccess expr,
			Env environment) {
		return null;
	}
	
	private Expr propagate(Expr.NaryOp expr,
			Env environment) {
		return null;
	}
	
	private Expr propagate(Expr.PackageAccess expr,
			Env environment) {
		return null;
	}
	
	private Expr propagate(Expr.RecordAccess expr,
			Env environment) {
		return null;
	}
	
	private Expr propagate(Expr.RecordGen expr,
			Env environment) {
		return null;
	}

	private Expr propagate(Expr.Spawn expr,
			Env environment) {
		return null;
	}

	private Expr propagate(Expr.TupleGen expr,
			Env environment) {
		return null;
	}
	
	private Expr propagate(Expr.TypeVal expr,
			Env environment) {
		return null;
	}
	
	private <T extends Type> T checkType(Type t, Class<T> clazz,
			SyntacticElement elem) {
		if (clazz.isInstance(t)) {
			return (T) t;
		} else {
			syntaxError(errorMessage(SUBTYPE_ERROR, clazz.getName(), t),
					filename, elem);
			return null;
		}
	}
	
	// Check t1 :> t2
	private void checkIsSubtype(Type t1, Type t2, SyntacticElement elem) {
		if (!Type.isImplicitCoerciveSubtype(t1, t2)) {			
			syntaxError(errorMessage(SUBTYPE_ERROR, t1, t2), filename, elem);
		}
	}		
	
	private abstract static class Scope {
		public abstract void free();
	}
	
	private static final class Handler {
		public final Type exception;
		public final String variable;
		public Env environment;
		
		public Handler(Type exception, String variable) {
			this.exception = exception;
			this.variable = variable;
			this.environment = new Env();
		}
	}
	
	private static final class TryCatchScope extends Scope {
		public final ArrayList<Handler> handlers = new ArrayList<Handler>();
						
		public void free() {
			for(Handler handler : handlers) {
				handler.environment.free();
			}
		}
	}
	
	private static final class BreakScope extends Scope {
		public Env environment;
		
		public void free() {
			environment.free();
		}
	}

	private static final class ContinueScope extends Scope {
		public Env environment;
		
		public void free() {
			environment.free();
		}
	}
	
	private static final Env BOTTOM = new Env();
	
	private static final class Env {
		private final HashMap<String,Type> types;
		private int count; // refCount
		
		public Env() {
			count = 1;
			types = new HashMap<String,Type>();
		}
		
		private Env(HashMap<String,Type> types) {
			count = 1;
			this.types = (HashMap<String,Type>) types.clone();
		}

		public Type get(String var) {
			return types.get(var);
		}
				
		public Env put(String var, Type type) {
			if(count == 1) {
				types.put(var, type);
				return this;
			} else {				
				Env nenv = new Env(types);
				nenv.types.put(var, type);
				count--;
				return nenv;
			}
		}
		
		public Env putAll(Env env) {
			if(count == 1) {
				HashMap<String,Type> envTypes = env.types;
				if(envTypes != null) {					
					types.putAll(envTypes);
				}
				return this;
			} else { 
				Env nenv = new Env(types);
				HashMap<String,Type> envTypes = env.types;
				if(envTypes != null) {
					nenv.types.putAll(envTypes);
				}
				count--;
				return nenv;				
			}
		}
		
		public Env remove(String var) {
			if(count == 1) {
				types.remove(var);
				return this;
			} else {				
				Env nenv = new Env(types);
				nenv.types.remove(var);
				count--;
				return nenv;
			}
		}
		
		public Env copy() {
			count++;
			return this;
		}
		
		public void free() {
			--count;			
		}
	}
}
