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

package wyil.util.dfa;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import wycc.lang.SyntaxError;
import wycc.util.Pair;
import wyil.attributes.SourceLocation;
import wyil.lang.*;
import wyil.util.*;
import static wyil.util.ErrorMessages.*;

public abstract class ForwardFlowAnalysis<T> {

	/**
	 * The filename of the module currently being propagated through
	 */
	protected String filename;

	/**
	 * The function or method currently being propagated through.
	 */
	protected WyilFile.FunctionOrMethodDeclaration method;

	/**
	 * The function or method case currently being propagated through.
	 */
	protected WyilFile.Case methodCase;

	/**
	 * The root block currently being propagated through.
	 */
	protected AttributedCodeBlock rootBlock;

	/**
	 * The temporary abstract stores being generated during propagation.
	 */
	protected HashMap<String, T> stores;

	public void apply(WyilFile module) {
		filename = module.filename();

		for(WyilFile.Block d : module.blocks()) {
			if(d instanceof WyilFile.ConstantDeclaration) {
				WyilFile.ConstantDeclaration cd = (WyilFile.ConstantDeclaration) d;
				module.replace(cd,propagate((cd)));
			} else if(d instanceof WyilFile.TypeDeclaration) {
				WyilFile.TypeDeclaration td = (WyilFile.TypeDeclaration) d;
				module.replace(td,propagate(td));
			} else if(d instanceof WyilFile.FunctionOrMethodDeclaration) {
				WyilFile.FunctionOrMethodDeclaration md = (WyilFile.FunctionOrMethodDeclaration) d;
				if (!md.hasModifier(Modifier.NATIVE)) {
					// native functions/methods don't have bodies
					module.replace(md,propagate(md));
				}
			}
		}
	}

	protected WyilFile.ConstantDeclaration propagate(WyilFile.ConstantDeclaration constant) {
		return constant;
	}

	protected WyilFile.TypeDeclaration propagate(WyilFile.TypeDeclaration type) {
		return type;
	}

	protected WyilFile.FunctionOrMethodDeclaration propagate(
			WyilFile.FunctionOrMethodDeclaration method) {
		this.method = method;
		ArrayList<WyilFile.Case> cases = new ArrayList<WyilFile.Case>();
		for (WyilFile.Case c : method.cases()) {
			cases.add(propagate(c));
		}
		return new WyilFile.FunctionOrMethodDeclaration(method.modifiers(),
				method.name(), method.type(), cases);
	}

	protected WyilFile.Case propagate(WyilFile.Case mcase) {
		this.methodCase = mcase;
		this.stores = new HashMap<String,T>();
		this.rootBlock = mcase.body();
		T init = initialStore();
		propagate(null, rootBlock, init, Collections.EMPTY_LIST);
		return mcase;
	}

	/**
	 * Propagate a given store forwards through this bytecode block. A list of
	 * exception handlers that are active is provided.
	 *
	 * @param parentIndex
	 *            The bytecode index of the bytecode containing this block, or
	 *            the empty index otherwise.
	 * @param block
	 *            The bytecode block to be propagated through.
	 * @param store
	 *            The store which holds at the end of this block.
	 * @param handlers
	 *            The list of active exception handlers
	 * @return
	 */
	protected T propagate(CodeBlock.Index parentIndex, CodeBlock block,
			T store,
			List<Codes.TryCatch> handlers) {

		for (int i = 0; i < block.size(); ++i) {
			Code code = block.get(i);

			// Construct the bytecode ID
			CodeBlock.Index id = new CodeBlock.Index(parentIndex,i);

			try {
				// First, check for a label which may have incoming information.
				if (code instanceof Codes.Label) {
					Codes.Label l = (Codes.Label) code;
					T tmp = stores.get(l.label);
					if (tmp != null && store != null) {
						store = join(store, tmp);
					} else if (tmp != null) {
						store = tmp;
					}
				}

				T oldStore = store;

				if (store == null) {
					// this indicates dead-code has been reached.
					continue;
				} else if (code instanceof Codes.Loop) {
					Codes.Loop loop = (Codes.Loop) code;
					// propagate through the loop body
					store = propagate(id, loop, store, handlers);
					continue;
				} else if (code instanceof Codes.If) {
					Codes.If ifgoto = (Codes.If) code;
					Pair<T, T> r = propagate(id, ifgoto, store);
					store = r.second();
					merge(ifgoto.target, r.first(), stores);
				} else if (code instanceof Codes.IfIs) {
					Codes.IfIs ifgoto = (Codes.IfIs) code;
					Pair<T, T> r = propagate(id, ifgoto, store);
					store = r.second();
					merge(ifgoto.target, r.first(), stores);
				} else if (code instanceof Codes.Switch) {
					Codes.Switch sw = (Codes.Switch) code;

					List<T> r = propagate(id, sw, store);

					// assert r.second().size() == nsw.branches.size()
					Codes.Switch nsw = (Codes.Switch) code;
					for (int j = 0; j != nsw.branches.size(); ++j) {
						String target = nsw.branches.get(j).second();
						T nstore = r.get(j);
						merge(target, nstore, stores);
					}
					merge(sw.defaultTarget, store, stores);
					store = null;
				} else if (code instanceof Codes.TryCatch) {
					Codes.TryCatch tc = (Codes.TryCatch) code;
					ArrayList<Codes.TryCatch> nhandlers = new ArrayList<Codes.TryCatch>(
							handlers);
					nhandlers.add(tc);
					store = propagate(id, tc, store, nhandlers);
				} else if (code instanceof Codes.Goto) {
					Codes.Goto gto = (Codes.Goto) code;
					merge(gto.target, store, stores);
					store = null;
				} else {
					// This indicates a sequential statement was encountered.
					store = propagate(id, code, store);
					if (code instanceof Codes.Return
							|| code instanceof Codes.Throw
							|| code instanceof Codes.Fail) {
						store = null;
					}
				}

				mergeHandlers(i, code, oldStore, handlers, stores);

			} catch (SyntaxError se) {
				throw se;
			} catch (Throwable ex) {
				internalFailure("internal failure", filename, ex, rootBlock.attribute(id,SourceLocation.class));
			}
		}

		return store;
	}

	private void merge(String target, T store, Map<String, T> stores) {
		T old = stores.get(target);
		if (old == null) {
			stores.put(target, store);
		} else {
			stores.put(target, join(old, store));
		}
	}

	protected void mergeHandlers(int index, Code code, T store, List<Codes.TryCatch> handlers,
			Map<String, T> stores) {
		if(code instanceof Codes.Throw) {
			Codes.Throw t = (Codes.Throw) code;
			mergeHandler(t.type,store,handlers,stores);
		} else if(code instanceof Codes.IndirectInvoke) {
			Codes.IndirectInvoke i = (Codes.IndirectInvoke) code;
			mergeHandler(i.type().throwsClause(),store,handlers,stores);
		} else if(code instanceof Codes.Invoke) {
			Codes.Invoke i = (Codes.Invoke) code;
			mergeHandler(i.type().throwsClause(),store,handlers,stores);
		}
	}

	protected void mergeHandler(Type type, T store, List<Codes.TryCatch> handlers,
			Map<String, T> stores) {
		for(int i=handlers.size()-1;i>=0;--i) {
			Codes.TryCatch tc = handlers.get(i);
			for(Pair<Type,String> p : tc.catches) {
				Type handler = p.first();

				if(Type.isSubtype(handler,type)) {
					T nstore = propagate(handler,tc,store);
					merge(p.second(),nstore,stores);
					return; // completely subsumed
				} else if(Type.isSubtype(type, handler)) {
					T nstore = propagate(handler,tc,store);
					merge(p.second(),nstore,stores);
					// not completely subsumed
					type = Type.intersect(type,Type.Negation(handler));
				}
			}
		}
	}

	/**
	 * <p>
	 * Propagate through a conditional branch. This produces two stores for the
	 * true and false branches respectively. The code of the statement returned
	 * is either that of the original statement, a Skip, or a Goto. The latter
	 * two indicate that the code was proven definitely false, or definitely
	 * true (respectively).
	 * </p>
	 *
	 * @param index
	 *            --- Index of bytecode in root CodeBlock
	 * @param ifgoto
	 *            --- the code of this statement
	 * @param store
	 *            --- abstract store which holds true immediately before this
	 *            statement.
	 * @return
	 */
	protected abstract Pair<T,T> propagate(CodeBlock.Index index, Codes.If ifgoto, T store);

	/**
	 * <p>
	 * Propagate through a type test. This produces two stores for the true and
	 * false branches respectively. The code of the statement returned is either
	 * that of the original statement, a Skip, or a Goto. The latter two
	 * indicate that the code was proven definitely false, or definitely true
	 * (respectively).
	 * </p>
	 *
	 * @param index
	 *            --- Index of bytecode in root CodeBlock
	 * @param iftype
	 *            --- the code of this statement
	 * @param store
	 *            --- abstract store which holds true immediately before this
	 *            statement.
	 * @return
	 */
	protected abstract Pair<T, T> propagate(CodeBlock.Index index, Codes.IfIs iftype, T store);

	/**
	 * <p>
	 * Propagate through a multi-way branch. This produces multiple stores ---
	 * one for each of the various branches.
	 * </p>
	 *
	 * @param index
	 *            --- Index of bytecode in root CodeBlock
	 * @param sw
	 *            --- the code of this statement
	 * @param store
	 *            --- abstract store which holds true immediately before this
	 *            statement.
	 * @return
	 */
	protected abstract List<T> propagate(CodeBlock.Index index, Codes.Switch sw, T store);

	/**
	 * Propagate an exception into a catch handler.
	 *
	 * @param handler
	 *            --- type of handler catching exception
	 * @param tc
	 *            --- the code of the enclosing try-catch handler
	 * @param store
	 *            --- store immediately before cause
	 * @return
	 */
	protected abstract T propagate(Type handler, Codes.TryCatch tc, T store);

	/**
	 * <p>
	 * Propagate through a loop statement, producing a store which holds true
	 * immediately after the statement
	 * </p>
	 * <p>
	 * <b>NOTE: the <code>start</code> index holds the loop code, whilst the
	 * <code>end</code> index holds the end code.
	 * </p>
	 *
	 * @param index
	 *            --- Index of bytecode in root block
	 * @param code
	 *            --- The loop bytecode
	 * @param entry
	 *            --- The block entry for the loop statement
	 * @param store
	 *            --- Abstract store which holds true immediately before this
	 *            statement.
	 * @return
	 */
	protected abstract T propagate(CodeBlock.Index index, Codes.Loop code, T store,
			List<Codes.TryCatch> handlers);

	/**
	 * <p>
	 * Propagate through a sequential statement, producing a store which holds
	 * true immediately after the statement
	 * </p>
	 *
	 * @param index
	 *            --- Index of bytecode in root CodeBlock
	 * @param code
	 *            --- Bytecode in question
	 * @param store
	 *            --- Abstract store which holds true immediately before this
	 *            statement.
	 * @return
	 */
	protected abstract T propagate(CodeBlock.Index index, Code code, T store);

	/**
	 * Determine the initial store for the current method case.
	 *
	 * @return
	 */
	protected abstract T initialStore();

	/**
	 * Join two abstract stores together producing a new abstract store. Observe
	 * that this operation must not side-effect the two input stores. This is
	 * because they may currently be stored in the stores map.
	 *
	 * @param store1
	 * @param store2
	 * @return
	 */
	protected abstract T join(T store1, T store2);
}
