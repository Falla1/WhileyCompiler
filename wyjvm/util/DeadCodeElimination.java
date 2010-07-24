// This file is part of the Wyjvm bytecode manipulation library.
//
// Wyjvm is free software; you can redistribute it and/or modify 
// it under the terms of the GNU General Public License as published 
// by the Free Software Foundation; either version 3 of the License, 
// or (at your option) any later version.
//
// Wyjvm is distributed in the hope that it will be useful, but 
// WITHOUT ANY WARRANTY; without even the implied warranty of 
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See 
// the GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public 
// License along with Wyjvm. If not, see <http://www.gnu.org/licenses/>
//
// Copyright 2010, David James Pearce. 

package wyjvm.util;

import java.util.*;

import wyil.util.Pair;
import wyjvm.attributes.*;
import wyjvm.lang.*;

/**
 * The purpose of this pass is to eliminate any statically determinable
 * dead-code. This is required to generate correct java bytecode. For example,
 * consider the following Java code:
 * 
 * <pre>
 * void f(...) {
 *  if(...) {
 *   return ...;
 *  else {
 *   return ...;
 *  }
 * }
 * </pre>
 * 
 * Then, the front-end will produce the following (pseudo) bytecode:
 * 
 * <pre>
 * void f(...) {
 *  if(...) goto iftrue0;
 *  return ...;
 *  goto ifexit0;
 * iftrue0:
 *   return ...;
 * ifexit0:
 * }
 * </pre>
 * 
 * Here, we can see quite clearly that the code after the first return
 * statement, as well as the ifexit label, is dead.
 * 
 * @author djp
 * 
 */
public class DeadCodeElimination {
	public void apply(ClassFile cf) {
		for(ClassFile.Method m : cf.methods()) {
			apply(m);
		}
	}
	
	public void apply(ClassFile.Method method) {
		for (BytecodeAttribute a : method.attributes()) {
			if(a instanceof Code) {
				apply((Code)a);
			}
		}
	}
	
	public void apply(Code code) {
		List<Bytecode> bytecodes = code.bytecodes();
		
		// First, perform depth-first search of method starting from first
		// bytecode. This identifies those bytecodes which are reachable from
		// the method's entry point.
		HashSet<Integer> visited = new HashSet<Integer>();
				
		// FIXME: there is a bug here related to exception handlers.
		visit(0,visited,buildLabelMap(bytecodes),bytecodes);
		
		// Second, for any unreachable bytecode, add a rewrite which simply
		// deletes it.
		ArrayList<Code.Rewrite> rewrites = new ArrayList<Code.Rewrite>();
		for(int i=0;i!=bytecodes.size();++i) {
			if(!visited.contains(i)) {				
				rewrites.add(new Code.Rewrite(i,1));
			}
		}
		
		code.apply(rewrites);
	}
	
	protected void visit(int index, HashSet<Integer> visited,
			HashMap<String,Integer> labels,
			List<Bytecode> bytecodes) {

		while(index < bytecodes.size() && !visited.contains(index)) {			
			visited.add(index);
			Bytecode b = bytecodes.get(index);

			if(b instanceof Bytecode.Goto) {
				Bytecode.Goto g = (Bytecode.Goto) b;
				int i = labels.get(g.label);
				if(!visited.contains(i)) {
					visit(i,visited,labels,bytecodes);
				}
				return;
			} else if(b instanceof Bytecode.Branch) {
				Bytecode.Branch g = (Bytecode.Branch) b;
				int i = labels.get(g.label);
				if(!visited.contains(i)) {
					visit(i,visited,labels,bytecodes);
				}
			} else if(b instanceof Bytecode.Switch) {
				Bytecode.Switch sw = (Bytecode.Switch) b;
				if (!visited.contains(labels.get(sw.defaultLabel))) {
					visit(labels.get(sw.defaultLabel), visited, labels,
							bytecodes);
				}
				for (Pair<Integer, String> p : sw.cases) {
					int i = labels.get(p.second());
					visit(i, visited, labels, bytecodes);
				}
			} else if (b instanceof Bytecode.Return
					|| b instanceof Bytecode.Throw) {
				// return + throw statements are simply dead-ends
				return;
			} 

			index = index + 1;			
		}
	}
	
	protected HashMap<String,Integer> buildLabelMap(List<Bytecode> bytecodes) {
		HashMap<String,Integer> map = new HashMap<String,Integer>();
		for(int i=0;i!=bytecodes.size();++i) {
			Bytecode b = bytecodes.get(i);
			if(b instanceof Bytecode.Label) {
				Bytecode.Label lab = (Bytecode.Label) b;
				map.put(lab.name, i);
			}
		}
		return map;
	}
}
