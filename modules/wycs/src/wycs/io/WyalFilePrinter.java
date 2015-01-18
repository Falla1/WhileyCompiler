package wycs.io;

import java.io.*;

import static wycc.lang.SyntaxError.*;
import wycc.lang.Attribute;
import wycc.lang.SyntacticElement;
import wycs.syntax.*;

public class WyalFilePrinter {
	public static final String INDENT = "  ";

	private PrintWriter out;

	public WyalFilePrinter(OutputStream writer) throws UnsupportedEncodingException {
		this(new OutputStreamWriter(writer,"UTF-8"));
	}

	public WyalFilePrinter(Writer writer) {
		this.out = new PrintWriter(writer);
	}

	public void write(WyalFile wf) {
		for(WyalFile.Declaration d : wf.declarations()) {
			write(wf, d);
			out.println();
		}
		out.flush();
	}

	private void write(WyalFile wf, WyalFile.Declaration s) {
		if(s instanceof WyalFile.Function) {
			write(wf,(WyalFile.Function)s);
		} else if(s instanceof WyalFile.Macro) {
			write(wf,(WyalFile.Macro)s);
		} else if(s instanceof WyalFile.Type) {
			write(wf,(WyalFile.Type)s);
		} else if(s instanceof WyalFile.Assert) {
			write(wf,(WyalFile.Assert)s);
		} else if(s instanceof WyalFile.Import) {
			write(wf,(WyalFile.Import)s);
		} else {
			internalFailure("unknown statement encountered " + s,
					wf.filename(), s);
		}
		out.println();
	}

	public void write(WyalFile wf, WyalFile.Import s) {
		String str = s.filter.toString();
		str = str.replace('/', '.');
		if (s.name == null) {
			out.print("import " + str);
		} else {
			out.print("import " + s.name + " from " + str);
		}
	}

	public void write(WyalFile wf, WyalFile.Function s) {
		out.print("function ");
		out.print(s.name);
		if(s.generics.size() > 0) {
			out.print("<");
			boolean firstTime=true;
			for(String g : s.generics) {
				if(!firstTime) {
					out.print(", ");
				}
				firstTime=false;
				out.print(g);
			}
			out.print("> ");
		}
		out.print(s.from + " => " + s.to);
		if(s.constraint != null) {
			out.println(" where:");
			indent(1);
			writeWithoutBraces(wf,s.constraint,1);
		}
	}
	
	public void write(WyalFile wf, WyalFile.Macro s) {
		out.print("define ");

		out.print(s.name);
		if(s.generics.size() > 0) {
			out.print("<");
			boolean firstTime=true;
			for(String g : s.generics) {
				if(!firstTime) {
					out.print(", ");
				}
				firstTime=false;
				out.print(g);
			}
			out.print(">");
		}
		writeWithBraces(wf,s.from);		
		if(s.body != null) {		
			out.println(" is:");
			indent(1);
			writeWithoutBraces(wf,s.body,1);
		}
	}

	public void write(WyalFile wf, WyalFile.Type s) {
		out.print("type ");
		out.print(s.name);
		if(s.generics.size() > 0) {
			out.print("<");
			boolean firstTime=true;
			for(String g : s.generics) {
				if(!firstTime) {
					out.print(", ");
				}
				firstTime=false;
				out.print(g);
			}
			out.print(">");
		}
		out.print(" is ");
		writeWithBraces(wf,s.type);		
		if(s.invariant != null) {
			out.println(" where:");			
			indent(1);
			writeWithoutBraces(wf,s.invariant,1);
		}
	}
	
	public void write(WyalFile wf, WyalFile.Assert s) {
		out.print("assert ");
		if(s.message != null) {
			out.print("\"" + s.message + "\"");
		}
		out.println(":");
		indent(1);
		writeWithoutBraces(wf,s.expr,1);
		out.println();
	}

	/**
	 * This function is called to print an expression which should be written
	 * with braces if it is not a single atomic entity.
	 *
	 * @param wf
	 * @param e
	 * @param indent
	 */
	public void writeWithBraces(WyalFile wf, Expr e, int indent) {
		out.print("(");
		writeWithoutBraces(wf,e,indent);
		out.print(")");		
	}
	
	/**
	 * This function is called to print an expression which should be written
	 * with braces if it is not a single atomic entity.
	 *
	 * @param wf
	 * @param e
	 * @param indent
	 */
	public void writeWithOptionalBraces(WyalFile wf, Expr e, int indent) {
		boolean needsBraces = needsBraces(e);
		if(needsBraces) {
			out.print("(");
			writeWithoutBraces(wf,e,indent);
			out.print(")");
		} else {
			writeWithoutBraces(wf,e,indent);
		}
	}

	public void writeWithoutBraces(WyalFile wf, Expr e, int indent) {
		if (e instanceof Expr.Constant || e instanceof Expr.Variable) {
			out.print(e);
		} else if(e instanceof Expr.Unary) {
			write(wf, (Expr.Unary)e,indent);
		} else if(e instanceof Expr.Cast) {
			write(wf, (Expr.Cast)e,indent);
		} else if(e instanceof Expr.Binary) {
			write(wf, (Expr.Binary)e,indent);
		} else if(e instanceof Expr.Ternary) {
			write(wf, (Expr.Ternary)e,indent);
		} else if(e instanceof Expr.Nary) {
			write(wf, (Expr.Nary)e,indent);
		} else if(e instanceof Expr.Quantifier) {
			write(wf, (Expr.Quantifier)e,indent);
		} else if(e instanceof Expr.Invoke) {
			write(wf, (Expr.Invoke)e,indent);
		} else if(e instanceof Expr.IndexOf) {
			write(wf, (Expr.IndexOf)e,indent);
		} else {
			internalFailure("unknown expression encountered " + e,
					wf.filename(), e);
		}
	}

	private void write(WyalFile wf, Expr.Cast e, int indent) {
		out.print("(" + e.type + ")" + e.operand);
	}
	
	private void write(WyalFile wf, Expr.Unary e, int indent) {
		switch(e.op) {
		case NOT:
			out.print("!");
			break;
		case NEG:
			out.print("-");
			break;
		case LENGTHOF:
			out.print("|");
			writeWithoutBraces(wf,e.operand,indent);
			out.print("|");
			return;
		}
		writeWithOptionalBraces(wf,e.operand,indent);
	}

	private void write(WyalFile wf, Expr.Binary e, int indent) {
		switch(e.op) {
		case IMPLIES:
			out.println("if:");
			indent(indent+1);
			writeWithoutBraces(wf,e.leftOperand,indent+1);
			out.println();
			indent(indent);
			out.println("then:");
			indent(indent+1);
			writeWithoutBraces(wf,e.rightOperand,indent+1);
			break;
		case AND:
			writeWithoutBraces(wf,e.leftOperand,indent);
			out.println();
			indent(indent);
			writeWithoutBraces(wf,e.rightOperand,indent);
			break;
		case OR:
			out.println("case:");
			indent(indent+1);
			writeWithoutBraces(wf,e.leftOperand,indent+1);
			out.println();
			indent(indent);
			out.println("case:");
			indent(indent+1);
			writeWithoutBraces(wf,e.rightOperand,indent+1);
			break;
		default:
			writeWithOptionalBraces(wf,e.leftOperand,indent);
			out.print(" " + e.op + " ");
			writeWithOptionalBraces(wf,e.rightOperand,indent);
		}
	}

	private void write(WyalFile wf, Expr.Ternary e, int indent) {
		switch(e.op) {
		case UPDATE:
			writeWithoutBraces(wf,e.firstOperand,indent);
			out.print("[");
			writeWithoutBraces(wf,e.secondOperand,indent);
			out.print(":=");
			writeWithoutBraces(wf,e.thirdOperand,indent);
			out.print("]");
			return;
		case SUBLIST:
			writeWithoutBraces(wf,e.firstOperand,indent);
			out.print("[");
			writeWithoutBraces(wf,e.secondOperand,indent);
			out.print("..");
			writeWithoutBraces(wf,e.thirdOperand,indent);
			out.print("]");
			return;
		}
		internalFailure("unknown expression encountered \"" + e + "\" (" + e.getClass().getName() + ")", wf.filename(), e);
	}


	private void write(WyalFile wf, Expr.Nary e, int indent) {
		switch(e.op) {
		case TUPLE:
		{
			boolean firstTime=true;
			for(Expr operand : e.operands) {
				if(!firstTime) {
					out.print(", ");
				} else {
					firstTime = false;
				}
				writeWithoutBraces(wf,operand,indent);
			}
			return;
		}
		case SET: {
			boolean firstTime=true;
			out.print("{");
			for(Expr operand : e.operands) {
				if(!firstTime) {
					out.print(", ");
				} else {
					firstTime = false;
				}
				writeWithOptionalBraces(wf,operand,indent);
			}
			out.print("}");
			return;
		}
		case MAP: {
			boolean firstTime=true;
			out.print("{");
			for(int i=0;i!=e.operands.size();i=i+2) {
				if(!firstTime) {
					out.print(", ");
				} else {
					firstTime = false;
				}
				writeWithOptionalBraces(wf,e.operands.get(i),indent);
				out.print(" => ");
				writeWithOptionalBraces(wf,e.operands.get(i+1),indent);
			}
			out.print("}");
			return;
		}
		case LIST: {
			boolean firstTime=true;
			out.print("[");
			for(Expr operand : e.operands) {
				if(!firstTime) {
					out.print(", ");
				} else {
					firstTime = false;
				}
				writeWithOptionalBraces(wf,operand,indent);
			}
			out.print("]");
			return;
		}
		}
		internalFailure("unknown expression encountered \"" + e + "\" (" + e.getClass().getName() + ")", wf.filename(), e);
	}

	private void write(WyalFile wf, Expr.Quantifier e, int indent) {
		if(e instanceof Expr.ForAll) {
			out.print("forall ");
		} else {
			out.print("exists ");
		}

		writeWithBraces(wf, e.pattern);
		out.println(":");
		indent(indent+1);
		writeWithoutBraces(wf,e.operand,indent+1);
	}

	private void write(WyalFile wf, Expr.Invoke e, int indent) {
		out.print(e.name);
		writeWithBraces(wf,e.operand,indent);
	}

	private void write(WyalFile wf, Expr.IndexOf e, int indent) {
		writeWithOptionalBraces(wf,e.operand,indent);
		out.print("[");
		out.print(e.index);
		out.print("]");
	}

	protected void writeWithoutBraces(WyalFile wf, TypePattern p) {
		if(p instanceof TypePattern.Tuple) {
			TypePattern.Tuple t = (TypePattern.Tuple) p;
			out.print("(");
			for(int i=0;i!=t.elements.size();++i) {
				if(i!=0) {
					out.print(", ");
				}
				writeWithoutBraces(wf,t.elements.get(i));
			}
			out.print(")");
		} else {
			TypePattern.Leaf l = (TypePattern.Leaf) p;
			out.print(l.type);
			if(l.var != null) {
				out.print(" " + l.var.name);
			}
		}
	}

	protected void writeWithBraces(WyalFile wf, TypePattern p) {
		out.print("(");
		if(p instanceof TypePattern.Tuple) {
			TypePattern.Tuple t = (TypePattern.Tuple) p;
			for(int i=0;i!=t.elements.size();++i) {
				if(i!=0) {
					out.print(", ");
				}
				writeWithoutBraces(wf,t.elements.get(i));
			}
		} else {
			TypePattern.Leaf l = (TypePattern.Leaf) p;
			out.print(l.type);
			if(l.var != null) {
				out.print(" " + l.var.name);
			}
		}
		out.print(")");
	}

	private static boolean needsBraces(Expr e) {
		 if(e instanceof Expr.Binary) {
			 Expr.Binary be = (Expr.Binary) e;
			 return true;
		 }
		 return false;
	}

	private void indent(int indent) {
		indent = indent * 4;
		for(int i=0;i<indent;++i) {
			out.print(" ");
		}
	}
}
