package org.printed.chat.interpreter;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Un intérprete básico de Common Lisp implementado en Java.
 * Esta clase permite evaluar expresiones Lisp simples.
 */
public class LispInterpreter {

    // Tipos de datos de Lisp
    public static abstract class LispObject {
        public abstract String toString();

        // Método por defecto que retorna false para evitar excepciones
        public boolean isSpecialForm() {
            return false;
        }

        // Método por defecto para aplicar funciones
        public LispObject apply(List<LispObject> args, Environment env) {
            throw new RuntimeException("Este objeto no es una función");
        }
    }

    public static class LispSymbol extends LispObject {
        private final String name;

        public LispSymbol(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        @Override
        public String toString() {
            return name;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            LispSymbol symbol = (LispSymbol) o;
            return name.equals(symbol.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name);
        }
    }

    public static class LispNumber extends LispObject {
        private final double value;

        public LispNumber(double value) {
            this.value = value;
        }

        public double getValue() {
            return value;
        }

        @Override
        public String toString() {
            if (value == (int) value) {
                return String.valueOf((int) value);
            }
            return String.valueOf(value);
        }
    }

    public static class LispList extends LispObject {
        private final List<LispObject> elements;

        public LispList() {
            this.elements = new ArrayList<>();
        }

        public LispList(List<LispObject> elements) {
            this.elements = new ArrayList<>(elements);
        }

        public List<LispObject> getElements() {
            return elements;
        }

        public void add(LispObject obj) {
            elements.add(obj);
        }

        public LispObject get(int index) {
            return elements.get(index);
        }

        public int size() {
            return elements.size();
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder("(");
            for (int i = 0; i < elements.size(); i++) {
                sb.append(elements.get(i).toString());
                if (i < elements.size() - 1) {
                    sb.append(" ");
                }
            }
            sb.append(")");
            return sb.toString();
        }
    }

    public static class LispString extends LispObject {
        private final String value;

        public LispString(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        @Override
        public String toString() {
            return "\"" + value + "\"";
        }
    }

    public static class LispBoolean extends LispObject {
        private final boolean value;

        public LispBoolean(boolean value) {
            this.value = value;
        }

        public boolean getValue() {
            return value;
        }

        @Override
        public String toString() {
            return value ? "t" : "nil";
        }
    }

    public static class LispFunction extends LispObject {
        private final LispList params;
        private final LispObject body;
        private final Environment closureEnv;

        public LispFunction(LispList params, LispObject body, Environment closureEnv) {
            this.params = params;
            this.body = body;
            this.closureEnv = closureEnv;
        }

        public LispList getParams() {
            return params;
        }

        public LispObject getBody() {
            return body;
        }

        public Environment getClosureEnv() {
            return closureEnv;
        }

        @Override
        public String toString() {
            return "#<FUNCTION " + params + " " + body + ">";
        }
    }

    // Definimos clases específicas para funciones nativas y formas especiales
    public static class NativeLispFunction extends LispObject {
        private final String name;
        private final NativeFunction function;

        public NativeLispFunction(String name, NativeFunction function) {
            this.name = name;
            this.function = function;
        }

        @Override
        public String toString() {
            return "#<NATIVE-FUNCTION " + name + ">";
        }

        @Override
        public LispObject apply(List<LispObject> args, Environment env) {
            return function.apply(args, env);
        }
    }

    public static class SpecialForm extends LispObject {
        private final String name;
        private final NativeFunction function;

        public SpecialForm(String name, NativeFunction function) {
            this.name = name;
            this.function = function;
        }

        @Override
        public String toString() {
            return "#<SPECIAL-FORM " + name + ">";
        }

        @Override
        public boolean isSpecialForm() {
            return true;
        }

        @Override
        public LispObject apply(List<LispObject> args, Environment env) {
            return function.apply(args, env);
        }
    }

    // Entorno para almacenar símbolos y sus valores
    public static class Environment {
        private final Map<String, LispObject> symbols;
        private final Environment parent;

        public Environment() {
            this(null);
        }

        public Environment(Environment parent) {
            this.symbols = new HashMap<>();
            this.parent = parent;
        }

        public void define(String symbol, LispObject value) {
            symbols.put(symbol, value);
        }

        public LispObject get(String symbol) {
            if (symbols.containsKey(symbol)) {
                return symbols.get(symbol);
            }
            if (parent != null) {
                return parent.get(symbol);
            }
            throw new RuntimeException("Símbolo no encontrado: " + symbol);
        }

        public boolean isDefined(String symbol) {
            return symbols.containsKey(symbol) || (parent != null && parent.isDefined(symbol));
        }

        public void set(String symbol, LispObject value) {
            if (symbols.containsKey(symbol)) {
                symbols.put(symbol, value);
            } else if (parent != null && parent.isDefined(symbol)) {
                parent.set(symbol, value);
            } else {
                throw new RuntimeException("Intento de asignar valor a un símbolo no definido: " + symbol);
            }
        }
    }

    // Funciones nativas
    private interface NativeFunction {
        LispObject apply(List<LispObject> args, Environment env);
    }

    private final Environment globalEnv;

    public LispInterpreter() {
        this.globalEnv = new Environment();
        setupGlobalEnvironment();
    }

    private void setupGlobalEnvironment() {
        // Constantes
        globalEnv.define("nil", new LispBoolean(false));
        globalEnv.define("t", new LispBoolean(true));

        // Operaciones aritméticas
        defineNativeFunction("+", (args, env) -> {
            double result = 0;
            for (LispObject arg : args) {
                if (arg instanceof LispNumber) {
                    result += ((LispNumber) arg).getValue();
                } else {
                    throw new RuntimeException("+ requiere argumentos numéricos");
                }
            }
            return new LispNumber(result);
        });

        defineNativeFunction("-", (args, env) -> {
            if (args.isEmpty()) {
                throw new RuntimeException("- requiere al menos un argumento");
            }

            if (args.size() == 1) {
                if (args.get(0) instanceof LispNumber) {
                    return new LispNumber(-((LispNumber) args.get(0)).getValue());
                }
                throw new RuntimeException("- requiere argumentos numéricos");
            }

            double result = ((LispNumber) args.get(0)).getValue();
            for (int i = 1; i < args.size(); i++) {
                if (args.get(i) instanceof LispNumber) {
                    result -= ((LispNumber) args.get(i)).getValue();
                } else {
                    throw new RuntimeException("- requiere argumentos numéricos");
                }
            }
            return new LispNumber(result);
        });

        defineNativeFunction("*", (args, env) -> {
            double result = 1;
            for (LispObject arg : args) {
                if (arg instanceof LispNumber) {
                    result *= ((LispNumber) arg).getValue();
                } else {
                    throw new RuntimeException("* requiere argumentos numéricos");
                }
            }
            return new LispNumber(result);
        });

        defineNativeFunction("/", (args, env) -> {
            if (args.isEmpty()) {
                throw new RuntimeException("/ requiere al menos un argumento");
            }

            if (args.size() == 1) {
                if (args.get(0) instanceof LispNumber) {
                    double value = ((LispNumber) args.get(0)).getValue();
                    if (value == 0) {
                        throw new RuntimeException("División por cero");
                    }
                    return new LispNumber(1 / value);
                }
                throw new RuntimeException("/ requiere argumentos numéricos");
            }

            if (!(args.get(0) instanceof LispNumber)) {
                throw new RuntimeException("/ requiere argumentos numéricos");
            }

            double result = ((LispNumber) args.get(0)).getValue();
            for (int i = 1; i < args.size(); i++) {
                if (args.get(i) instanceof LispNumber) {
                    double divisor = ((LispNumber) args.get(i)).getValue();
                    if (divisor == 0) {
                        throw new RuntimeException("División por cero");
                    }
                    result /= divisor;
                } else {
                    throw new RuntimeException("/ requiere argumentos numéricos");
                }
            }
            return new LispNumber(result);
        });

        // Comparaciones
        defineNativeFunction("=", (args, env) -> {
            if (args.size() < 2) {
                throw new RuntimeException("= requiere al menos dos argumentos");
            }

            if (!(args.get(0) instanceof LispNumber)) {
                throw new RuntimeException("= solo compara números");
            }

            double first = ((LispNumber) args.get(0)).getValue();
            for (int i = 1; i < args.size(); i++) {
                if (!(args.get(i) instanceof LispNumber)) {
                    throw new RuntimeException("= solo compara números");
                }
                if (first != ((LispNumber) args.get(i)).getValue()) {
                    return new LispBoolean(false);
                }
            }
            return new LispBoolean(true);
        });

        defineNativeFunction("<", (args, env) -> {
            if (args.size() < 2) {
                throw new RuntimeException("< requiere al menos dos argumentos");
            }

            for (int i = 0; i < args.size() - 1; i++) {
                if (!(args.get(i) instanceof LispNumber) || !(args.get(i + 1) instanceof LispNumber)) {
                    throw new RuntimeException("< solo compara números");
                }
                if (!(((LispNumber) args.get(i)).getValue() < ((LispNumber) args.get(i + 1)).getValue())) {
                    return new LispBoolean(false);
                }
            }
            return new LispBoolean(true);
        });

        defineNativeFunction(">", (args, env) -> {
            if (args.size() < 2) {
                throw new RuntimeException("> requiere al menos dos argumentos");
            }

            for (int i = 0; i < args.size() - 1; i++) {
                if (!(args.get(i) instanceof LispNumber) || !(args.get(i + 1) instanceof LispNumber)) {
                    throw new RuntimeException("> solo compara números");
                }
                if (!(((LispNumber) args.get(i)).getValue() > ((LispNumber) args.get(i + 1)).getValue())) {
                    return new LispBoolean(false);
                }
            }
            return new LispBoolean(true);
        });

        // Funciones de lista
        defineNativeFunction("list", (args, env) -> new LispList(args));

        defineNativeFunction("car", (args, env) -> {
            if (args.size() != 1) {
                throw new RuntimeException("car requiere exactamente un argumento");
            }
            if (!(args.get(0) instanceof LispList)) {
                throw new RuntimeException("car requiere una lista como argumento");
            }
            LispList list = (LispList) args.get(0);
            if (list.size() == 0) {
                return new LispBoolean(false); // nil
            }
            return list.get(0);
        });

        defineNativeFunction("cdr", (args, env) -> {
            if (args.size() != 1) {
                throw new RuntimeException("cdr requiere exactamente un argumento");
            }
            if (!(args.get(0) instanceof LispList)) {
                throw new RuntimeException("cdr requiere una lista como argumento");
            }
            LispList list = (LispList) args.get(0);
            if (list.size() <= 1) {
                return new LispList();
            }
            return new LispList(list.getElements().subList(1, list.size()));
        });

        defineNativeFunction("cons", (args, env) -> {
            if (args.size() != 2) {
                throw new RuntimeException("cons requiere exactamente dos argumentos");
            }
            LispObject first = args.get(0);
            if (!(args.get(1) instanceof LispList)) {
                throw new RuntimeException("El segundo argumento de cons debe ser una lista");
            }
            LispList rest = (LispList) args.get(1);
            LispList result = new LispList();
            result.add(first);
            for (LispObject obj : rest.getElements()) {
                result.add(obj);
            }
            return result;
        });

        // Operadores lógicos
        defineSpecialForm("and", (args, env) -> {
            LispObject result = new LispBoolean(true);
            for (LispObject arg : args) {
                result = eval(arg, env);
                if (result instanceof LispBoolean && !((LispBoolean) result).getValue()) {
                    return result;
                }
            }
            return result;
        });

        defineSpecialForm("or", (args, env) -> {
            for (LispObject arg : args) {
                LispObject result = eval(arg, env);
                if (!(result instanceof LispBoolean) || ((LispBoolean) result).getValue()) {
                    return result;
                }
            }
            return new LispBoolean(false);
        });

        defineNativeFunction("not", (args, env) -> {
            if (args.size() != 1) {
                throw new RuntimeException("not requiere exactamente un argumento");
            }
            LispObject arg = args.get(0);
            if (arg instanceof LispBoolean) {
                return new LispBoolean(!((LispBoolean) arg).getValue());
            }
            return new LispBoolean(false);
        });

        // Definición de variables y funciones
        defineSpecialForm("defvar", (args, env) -> {
            if (args.size() != 2) {
                throw new RuntimeException("defvar requiere exactamente dos argumentos");
            }
            if (!(args.get(0) instanceof LispSymbol)) {
                throw new RuntimeException("El primer argumento de defvar debe ser un símbolo");
            }
            String name = ((LispSymbol) args.get(0)).getName();
            LispObject value = eval(args.get(1), env);
            env.define(name, value);
            return value;
        });

        defineSpecialForm("setq", (args, env) -> {
            if (args.size() != 2) {
                throw new RuntimeException("setq requiere exactamente dos argumentos");
            }
            if (!(args.get(0) instanceof LispSymbol)) {
                throw new RuntimeException("El primer argumento de setq debe ser un símbolo");
            }
            String name = ((LispSymbol) args.get(0)).getName();
            LispObject value = eval(args.get(1), env);
            env.set(name, value);
            return value;
        });

        defineSpecialForm("defun", (args, env) -> {
            if (args.size() < 3) {
                throw new RuntimeException("defun requiere al menos tres argumentos");
            }
            if (!(args.get(0) instanceof LispSymbol)) {
                throw new RuntimeException("El primer argumento de defun debe ser un símbolo");
            }
            String name = ((LispSymbol) args.get(0)).getName();

            if (!(args.get(1) instanceof LispList)) {
                throw new RuntimeException("El segundo argumento de defun debe ser una lista de parámetros");
            }
            LispList params = (LispList) args.get(1);

            // Crear el cuerpo de la función
            LispList body = new LispList();
            for (int i = 2; i < args.size(); i++) {
                body.add(args.get(i));
            }

            // Crear la función
            LispFunction function = new LispFunction(params, body, env);
            env.define(name, function);

            return new LispSymbol(name);
        });

        defineSpecialForm("lambda", (args, env) -> {
            if (args.size() < 2) {
                throw new RuntimeException("lambda requiere al menos dos argumentos");
            }
            if (!(args.get(0) instanceof LispList)) {
                throw new RuntimeException("El primer argumento de lambda debe ser una lista de parámetros");
            }

            LispList params = (LispList) args.get(0);

            // Crear el cuerpo de la función
            LispList body = new LispList();
            for (int i = 1; i < args.size(); i++) {
                body.add(args.get(i));
            }

            return new LispFunction(params, body, env);
        });

        // Control de flujo
        defineSpecialForm("if", (args, env) -> {
            if (args.size() < 2 || args.size() > 3) {
                throw new RuntimeException("if requiere dos o tres argumentos");
            }

            LispObject condition = eval(args.get(0), env);
            boolean condResult = !(condition instanceof LispBoolean) || ((LispBoolean) condition).getValue();

            if (condResult) {
                return eval(args.get(1), env);
            } else if (args.size() == 3) {
                return eval(args.get(2), env);
            } else {
                return new LispBoolean(false); // nil
            }
        });

        defineSpecialForm("cond", (args, env) -> {
            for (LispObject arg : args) {
                if (!(arg instanceof LispList)) {
                    throw new RuntimeException("Las cláusulas de cond deben ser listas");
                }

                LispList clause = (LispList) arg;
                if (clause.size() < 1) {
                    throw new RuntimeException("Las cláusulas de cond no pueden estar vacías");
                }

                LispObject condition = eval(clause.get(0), env);
                boolean condResult = !(condition instanceof LispBoolean) || ((LispBoolean) condition).getValue();

                if (condResult) {
                    if (clause.size() == 1) {
                        return condition;
                    } else {
                        LispObject result = null;
                        for (int i = 1; i < clause.size(); i++) {
                            result = eval(clause.get(i), env);
                        }
                        return result;
                    }
                }
            }
            return new LispBoolean(false); // nil
        });

        // Salida
        defineNativeFunction("print", (args, env) -> {
            for (LispObject arg : args) {
                System.out.print(arg);
                System.out.print(" ");
            }
            System.out.println();
            return args.isEmpty() ? new LispBoolean(false) : args.get(args.size() - 1);
        });
    }

    private void defineNativeFunction(String name, NativeFunction function) {
        globalEnv.define(name, new NativeLispFunction(name, function));
    }

    private void defineSpecialForm(String name, NativeFunction function) {
        globalEnv.define(name, new SpecialForm(name, function));
    }

    /**
     * Tokeniza una expresión Lisp.
     */
    private List<String> tokenize(String input) {
        List<String> tokens = new ArrayList<>();
        Pattern pattern = Pattern.compile("\\(|\\)|[^\\s\\(\\)]+");
        Matcher matcher = pattern.matcher(input);

        while (matcher.find()) {
            tokens.add(matcher.group());
        }

        return tokens;
    }

    /**
     * Convierte una lista de tokens en una expresión Lisp.
     */
    private LispObject parse(List<String> tokens) {
        if (tokens.isEmpty()) {
            throw new RuntimeException("Expresión inesperada: fin de entrada");
        }

        String token = tokens.remove(0);

        if ("(".equals(token)) {
            LispList list = new LispList();
            while (!tokens.isEmpty() && !")".equals(tokens.get(0))) {
                list.add(parse(tokens));
            }

            if (tokens.isEmpty()) {
                throw new RuntimeException("Paréntesis no cerrado");
            }

            // Eliminar el paréntesis de cierre
            tokens.remove(0);
            return list;
        } else if (")".equals(token)) {
            throw new RuntimeException("Paréntesis de cierre inesperado");
        } else {
            // Intentar analizar el token como un número
            try {
                return new LispNumber(Double.parseDouble(token));
            } catch (NumberFormatException e) {
                // Comprobar si es una cadena (entre comillas dobles)
                if (token.startsWith("\"") && token.endsWith("\"")) {
                    return new LispString(token.substring(1, token.length() - 1));
                }

                // Símbolos especiales
                if ("nil".equals(token)) {
                    return new LispBoolean(false);
                } else if ("t".equals(token)) {
                    return new LispBoolean(true);
                }

                // Es un símbolo
                return new LispSymbol(token);
            }
        }
    }

    /**
     * Evalúa una expresión Lisp en el entorno dado.
     */
    public LispObject eval(LispObject expr, Environment env) {
        if (expr instanceof LispSymbol) {
            String name = ((LispSymbol) expr).getName();
            return env.get(name);
        } else if (expr instanceof LispNumber || expr instanceof LispString || expr instanceof LispBoolean) {
            return expr;
        } else if (expr instanceof LispFunction) {
            return expr;
        } else if (expr instanceof LispList) {
            LispList list = (LispList) expr;
            if (list.size() == 0) {
                return list;
            }

            LispObject first = list.get(0);
            if (first instanceof LispSymbol) {
                String funcName = ((LispSymbol) first).getName();
                LispObject func = env.get(funcName);

                // Comprobar si es una forma especial
                if (func != null && func.isSpecialForm()) {
                    List<LispObject> args = new ArrayList<>();
                    for (int i = 1; i < list.size(); i++) {
                        args.add(list.get(i));
                    }
                    return func.apply(args, env);
                }

                // Evaluar los argumentos
                List<LispObject> args = new ArrayList<>();
                for (int i = 1; i < list.size(); i++) {
                    args.add(eval(list.get(i), env));
                }

                // Aplicar la función
                if (func != null) {
                    return func.apply(args, env);
                } else {
                    throw new RuntimeException("Función no definida: " + ((LispSymbol) first).getName());
                }
            } else {
                // La primera expresión debe evaluar a una función
                LispObject func = eval(first, env);

                if (func instanceof LispFunction) {
                    LispFunction function = (LispFunction) func;
                    List<LispObject> args = new ArrayList<>();
                    for (int i = 1; i < list.size(); i++) {
                        args.add(eval(list.get(i), env));
                    }

                    // Crear un nuevo entorno para la función
                    Environment funcEnv = new Environment(function.getClosureEnv());
                    LispList params = function.getParams();

                    // Enlazar los parámetros a los argumentos
                    for (int i = 0; i < Math.min(params.size(), args.size()); i++) {
                        if (!(params.get(i) instanceof LispSymbol)) {
                            throw new RuntimeException("Los parámetros de función deben ser símbolos");
                        }
                        String paramName = ((LispSymbol) params.get(i)).getName();
                        funcEnv.define(paramName, args.get(i));
                    }

                    // Evaluar el cuerpo de la función
                    LispObject body = function.getBody();
                    if (body instanceof LispList) {
                        LispList bodyList = (LispList) body;
                        LispObject result = new LispBoolean(false); // nil
                        for (LispObject expr2 : bodyList.getElements()) {
                            result = eval(expr2, funcEnv);
                        }
                        return result;
                    } else {
                        return eval(body, funcEnv);
                    }
                } else {
                    throw new RuntimeException("La primera expresión debe evaluar a una función");
                }
            }
        }

        throw new RuntimeException("No se puede evaluar la expresión: " + expr);
    }

    /**
     * Evalúa una expresión Lisp en el entorno global.
     */
    public LispObject eval(String input) {
        List<String> tokens = tokenize(input);
        LispObject expr = parse(tokens);
        return eval(expr, globalEnv);
    }

    /**
     * REPL (Read-Eval-Print Loop) para interactuar con el intérprete.
     */
    public void repl() {
        Scanner scanner = new Scanner(System.in);
        System.out.println("Intérprete de Lisp");
        System.out.println("Escribe 'exit' para salir");

        while (true) {
            System.out.print("> ");
            String input = scanner.nextLine().trim();

            if ("exit".equalsIgnoreCase(input)) {
                break;
            }

            try {
                LispObject result = eval(input);
                System.out.println(result);
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
            }
        }

        scanner.close();
    }

    public static void main(String[] args) {
        LispInterpreter interpreter = new LispInterpreter();
        interpreter.repl();
    }
}