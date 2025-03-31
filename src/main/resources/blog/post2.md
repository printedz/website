# Creando un Intérprete de Lisp en Rust con un Solo Archivo
¡Hola a todos! En este segundo post voy a compartir algo fascinante: cómo crear un intérprete de Lisp utilizando Rust en un único archivo. Este proyecto es perfecto para quienes quieren explorar conceptos de compiladores e intérpretes sin sumergirse en proyectos excesivamente complejos.
## ¿Por qué Lisp?
Lisp es uno de los lenguajes de programación más antiguos y elegantes que existen. Creado en 1958, su sintaxis basada en expresiones S (S-expressions) lo hace ideal para un intérprete sencillo:
- Sintaxis uniforme y minimalista
- Representación de código y datos con la misma estructura
- Modelo de evaluación simple pero poderoso

## ¿Por qué Rust?
Rust es perfecto para este proyecto por varias razones:
- **Seguridad de memoria**: Sin fugas de memoria ni condiciones de carrera
- **Rendimiento**: Velocidad comparable a C/C++ sin el dolor de cabeza
- **Expresividad**: Su sistema de tipos y pattern matching facilitan el manejo de estructuras recursivas como Lisp
- **Manejo de errores**: El tipo `Result` es perfecto para un intérprete robusto

## Nuestro Mini-Lisp
Vamos a crear un dialecto simple de Lisp que soporte:
- Tipos básicos (números, símbolos, booleanos)
- Operaciones aritméticas
- Definición de variables y funciones
- Evaluación de expresiones

## El código completo en un archivo
``` rust
// mini_lisp.rs - Un intérprete de Lisp en un solo archivo Rust

use std::collections::HashMap;
use std::fmt;
use std::rc::Rc;
use std::cell::RefCell;
use std::io::{self, BufRead, Write};
use std::fs;

// Definición de nuestros tipos principales
#[derive(Clone)]
enum LispVal {
    Symbol(String),
    Number(f64),
    Bool(bool),
    List(Vec<LispVal>),
    Func(fn(&[LispVal]) -> Result<LispVal, String>),
    Lambda(Lambda),
    Nil,
}

#[derive(Clone)]
struct Lambda {
    params: Vec<String>,
    body: Vec<LispVal>,
    env: Rc<RefCell<Environment>>,
}

// Impresión agradable para nuestros valores Lisp
impl fmt::Display for LispVal {
    fn fmt(&self, f: &mut fmt::Formatter) -> fmt::Result {
        match self {
            LispVal::Symbol(s) => write!(f, "{}", s),
            LispVal::Number(n) => {
                if n.fract() == 0.0 {
                    write!(f, "{}", *n as i64)
                } else {
                    write!(f, "{}", n)
                }
            },
            LispVal::Bool(b) => write!(f, "{}", if *b { "#t" } else { "#f" }),
            LispVal::List(list) => {
                write!(f, "(")?;
                for (i, val) in list.iter().enumerate() {
                    if i > 0 { write!(f, " ")?; }
                    write!(f, "{}", val)?;
                }
                write!(f, ")")
            },
            LispVal::Func(_) => write!(f, "<función nativa>"),
            LispVal::Lambda(_) => write!(f, "<lambda>"),
            LispVal::Nil => write!(f, "nil"),
        }
    }
}

// Nuestro entorno para almacenar variables y funciones
struct Environment {
    vars: HashMap<String, LispVal>,
    outer: Option<Rc<RefCell<Environment>>>,
}

impl Environment {
    fn new() -> Self {
        Environment {
            vars: HashMap::new(),
            outer: None,
        }
    }
    
    fn with_outer(outer: Rc<RefCell<Environment>>) -> Self {
        Environment {
            vars: HashMap::new(),
            outer: Some(outer),
        }
    }
    
    fn get(&self, key: &str) -> Option<LispVal> {
        match self.vars.get(key) {
            Some(val) => Some(val.clone()),
            None => match &self.outer {
                Some(outer) => outer.borrow().get(key),
                None => None,
            },
        }
    }
    
    fn set(&mut self, key: String, val: LispVal) {
        self.vars.insert(key, val);
    }
}

// Analizador léxico y sintáctico (Parser)
fn tokenize(input: &str) -> Vec<String> {
    let mut tokens = Vec::new();
    let mut current = String::new();
    
    for c in input.chars() {
        match c {
            '(' | ')' => {
                if !current.is_empty() {
                    tokens.push(current);
                    current = String::new();
                }
                tokens.push(c.to_string());
            }
            ' ' | '\n' | '\t' | '\r' => {
                if !current.is_empty() {
                    tokens.push(current);
                    current = String::new();
                }
            }
            _ => current.push(c),
        }
    }
    
    if !current.is_empty() {
        tokens.push(current);
    }
    
    tokens
}

fn parse<'a, I>(tokens: &mut I) -> Result<LispVal, String>
where
    I: Iterator<Item = &'a String>,
{
    match tokens.next() {
        Some(token) => {
            match token.as_str() {
                "(" => {
                    let mut list = Vec::new();
                    loop {
                        match tokens.next() {
                            Some(t) if t == ")" => break,
                            Some(t) => {
                                if t == "(" {
                                    // Devolvemos el token "(" al flujo y analizamos recursivamente
                                    let mut new_tokens = vec![t].into_iter();
                                    let parsed = parse(&mut new_tokens)?;
                                    list.push(parsed);
                                    continue;
                                }
                                tokens = &mut std::iter::once(t).chain(tokens);
                                let expr = parse(tokens)?;
                                list.push(expr);
                            }
                            None => return Err("Error de sintaxis: Falta paréntesis de cierre".to_string()),
                        }
                    }
                    Ok(LispVal::List(list))
                }
                ")" => Err("Error de sintaxis: Paréntesis de cierre inesperado".to_string()),
                _ => {
                    // Analizar átomos
                    if let Ok(n) = token.parse::<f64>() {
                        Ok(LispVal::Number(n))
                    } else if token == "#t" {
                        Ok(LispVal::Bool(true))
                    } else if token == "#f" {
                        Ok(LispVal::Bool(false))
                    } else if token == "nil" {
                        Ok(LispVal::Nil)
                    } else {
                        Ok(LispVal::Symbol(token.clone()))
                    }
                }
            }
        }
        None => Err("Error de sintaxis: Entrada vacía".to_string()),
    }
}

fn parse_expr(input: &str) -> Result<LispVal, String> {
    let tokens = tokenize(input);
    let mut iter = tokens.iter();
    parse(&mut iter)
}

// Evaluación de expresiones
fn eval(expr: &LispVal, env: Rc<RefCell<Environment>>) -> Result<LispVal, String> {
    match expr {
        LispVal::Symbol(s) => {
            match env.borrow().get(s) {
                Some(val) => Ok(val),
                None => Err(format!("Símbolo no definido: {}", s)),
            }
        },
        LispVal::Number(_) | LispVal::Bool(_) | LispVal::Nil => Ok(expr.clone()),
        LispVal::List(list) => {
            if list.is_empty() {
                return Ok(LispVal::Nil);
            }
            
            // Formas especiales
            if let LispVal::Symbol(s) = &list[0] {
                match s.as_str() {
                    "quote" => {
                        if list.len() != 2 {
                            return Err("'quote' requiere exactamente 1 argumento".to_string());
                        }
                        Ok(list[1].clone())
                    },
                    "if" => {
                        if list.len() != 4 {
                            return Err("'if' requiere exactamente 3 argumentos".to_string());
                        }
                        let cond = eval(&list[1], Rc::clone(&env))?;
                        match cond {
                            LispVal::Bool(false) | LispVal::Nil => eval(&list[3], env),
                            _ => eval(&list[2], env),
                        }
                    },
                    "define" => {
                        if list.len() < 3 {
                            return Err("'define' requiere al menos 2 argumentos".to_string());
                        }
                        match &list[1] {
                            LispVal::Symbol(name) => {
                                let val = eval(&list[2], Rc::clone(&env))?;
                                env.borrow_mut().set(name.clone(), val.clone());
                                Ok(val)
                            },
                            LispVal::List(func_def) => {
                                if func_def.is_empty() {
                                    return Err("Definición de función inválida".to_string());
                                }
                                if let LispVal::Symbol(name) = &func_def[0] {
                                    let params = func_def.iter().skip(1)
                                        .map(|param| {
                                            if let LispVal::Symbol(s) = param {
                                                Ok(s.clone())
                                            } else {
                                                Err("Parámetro de función inválido".to_string())
                                            }
                                        })
                                        .collect::<Result<Vec<_>, _>>()?;
                                    
                                    let body = list.iter().skip(2).cloned().collect();
                                    let lambda = LispVal::Lambda(Lambda {
                                        params,
                                        body,
                                        env: Rc::clone(&env),
                                    });
                                    env.borrow_mut().set(name.clone(), lambda.clone());
                                    Ok(lambda)
                                } else {
                                    Err("Nombre de función inválido".to_string())
                                }
                            },
                            _ => Err("'define' requiere un símbolo o lista como primer argumento".to_string()),
                        }
                    },
                    "lambda" => {
                        if list.len() < 3 {
                            return Err("'lambda' requiere al menos 2 argumentos".to_string());
                        }
                        let params_list = if let LispVal::List(params) = &list[1] {
                            params
                        } else {
                            return Err("Los parámetros de lambda deben ser una lista".to_string());
                        };
                        
                        let params = params_list.iter()
                            .map(|param| {
                                if let LispVal::Symbol(s) = param {
                                    Ok(s.clone())
                                } else {
                                    Err("Parámetro de lambda inválido".to_string())
                                }
                            })
                            .collect::<Result<Vec<_>, _>>()?;
                        
                        let body = list.iter().skip(2).cloned().collect();
                        Ok(LispVal::Lambda(Lambda {
                            params,
                            body,
                            env: Rc::clone(&env),
                        }))
                    },
                    "let" => {
                        if list.len() < 3 {
                            return Err("'let' requiere al menos 2 argumentos".to_string());
                        }
                        let bindings = if let LispVal::List(binds) = &list[1] {
                            binds
                        } else {
                            return Err("Las asignaciones de 'let' deben ser una lista".to_string());
                        };
                        
                        let local_env = Rc::new(RefCell::new(Environment::with_outer(Rc::clone(&env))));
                        
                        for binding in bindings {
                            if let LispVal::List(pair) = binding {
                                if pair.len() != 2 {
                                    return Err("Cada asignación de 'let' debe tener exactamente 2 elementos".to_string());
                                }
                                if let LispVal::Symbol(var) = &pair[0] {
                                    let val = eval(&pair[1], Rc::clone(&env))?;
                                    local_env.borrow_mut().set(var.clone(), val);
                                } else {
                                    return Err("El primer elemento de una asignación debe ser un símbolo".to_string());
                                }
                            } else {
                                return Err("Asignación de 'let' inválida".to_string());
                            }
                        }
                        
                        let mut result = LispVal::Nil;
                        for expr in list.iter().skip(2) {
                            result = eval(expr, Rc::clone(&local_env))?;
                        }
                        Ok(result)
                    },
                    _ => {
                        // Invocación de función
                        let func_val = eval(&list[0], Rc::clone(&env))?;
                        let args = list.iter().skip(1)
                            .map(|arg| eval(arg, Rc::clone(&env)))
                            .collect::<Result<Vec<_>, _>>()?;
                        
                        apply_function(func_val, &args)
                    }
                }
            } else {
                // La primera posición debe evaluarse a una función
                let func_val = eval(&list[0], Rc::clone(&env))?;
                let args = list.iter().skip(1)
                    .map(|arg| eval(arg, Rc::clone(&env)))
                    .collect::<Result<Vec<_>, _>>()?;
                
                apply_function(func_val, &args)
            }
        },
        LispVal::Func(_) | LispVal::Lambda(_) => Ok(expr.clone()),
    }
}

fn apply_function(func: LispVal, args: &[LispVal]) -> Result<LispVal, String> {
    match func {
        LispVal::Func(f) => f(args),
        LispVal::Lambda(Lambda { params, body, env }) => {
            if params.len() != args.len() {
                return Err(format!(
                    "Se esperaban {} argumentos, se recibieron {}",
                    params.len(),
                    args.len()
                ));
            }
            
            let local_env = Rc::new(RefCell::new(Environment::with_outer(env)));
            
            // Asociar argumentos con parámetros
            for (param, arg) in params.iter().zip(args.iter()) {
                local_env.borrow_mut().set(param.clone(), arg.clone());
            }
            
            // Evaluar el cuerpo de la función
            let mut result = LispVal::Nil;
            for expr in body {
                result = eval(&expr, Rc::clone(&local_env))?;
            }
            
            Ok(result)
        },
        _ => Err("No es una función aplicable".to_string()),
    }
}

// Funciones nativas
fn add(args: &[LispVal]) -> Result<LispVal, String> {
    let sum = args.iter().try_fold(0.0, |acc, val| {
        if let LispVal::Number(n) = val {
            Ok(acc + n)
        } else {
            Err(format!("'+' espera números, recibió {}", val))
        }
    })?;
    Ok(LispVal::Number(sum))
}

fn subtract(args: &[LispVal]) -> Result<LispVal, String> {
    if args.is_empty() {
        return Err("'-' requiere al menos un argumento".to_string());
    }
    
    if args.len() == 1 {
        if let LispVal::Number(n) = args[0] {
            return Ok(LispVal::Number(-n));
        } else {
            return Err(format!("'-' espera un número, recibió {}", args[0]));
        }
    }
    
    let mut iter = args.iter();
    let first = if let LispVal::Number(n) = iter.next().unwrap() {
        *n
    } else {
        return Err(format!("'-' espera números, recibió {}", args[0]));
    };
    
    let result = iter.try_fold(first, |acc, val| {
        if let LispVal::Number(n) = val {
            Ok(acc - n)
        } else {
            Err(format!("'-' espera números, recibió {}", val))
        }
    })?;
    
    Ok(LispVal::Number(result))
}

fn multiply(args: &[LispVal]) -> Result<LispVal, String> {
    let product = args.iter().try_fold(1.0, |acc, val| {
        if let LispVal::Number(n) = val {
            Ok(acc * n)
        } else {
            Err(format!("'*' espera números, recibió {}", val))
        }
    })?;
    Ok(LispVal::Number(product))
}

fn divide(args: &[LispVal]) -> Result<LispVal, String> {
    if args.is_empty() {
        return Err("'/' requiere al menos un argumento".to_string());
    }
    
    let mut iter = args.iter();
    let first = if let LispVal::Number(n) = iter.next().unwrap() {
        *n
    } else {
        return Err(format!("'/' espera números, recibió {}", args[0]));
    };
    
    if args.len() == 1 {
        return Ok(LispVal::Number(1.0 / first));
    }
    
    let result = iter.try_fold(first, |acc, val| {
        if let LispVal::Number(n) = val {
            if *n == 0.0 {
                Err("División por cero".to_string())
            } else {
                Ok(acc / n)
            }
        } else {
            Err(format!("'/' espera números, recibió {}", val))
        }
    })?;
    
    Ok(LispVal::Number(result))
}

fn equals(args: &[LispVal]) -> Result<LispVal, String> {
    if args.len() != 2 {
        return Err("'=' requiere exactamente 2 argumentos".to_string());
    }
    
    let result = match (&args[0], &args[1]) {
        (LispVal::Number(a), LispVal::Number(b)) => a == b,
        (LispVal::Bool(a), LispVal::Bool(b)) => a == b,
        (LispVal::Symbol(a), LispVal::Symbol(b)) => a == b,
        (LispVal::Nil, LispVal::Nil) => true,
        _ => false,
    };
    
    Ok(LispVal::Bool(result))
}

fn less_than(args: &[LispVal]) -> Result<LispVal, String> {
    if args.len() != 2 {
        return Err("'<' requiere exactamente 2 argumentos".to_string());
    }
    
    match (&args[0], &args[1]) {
        (LispVal::Number(a), LispVal::Number(b)) => Ok(LispVal::Bool(a < b)),
        _ => Err(format!("'<' espera números, recibió {} y {}", args[0], args[1])),
    }
}

fn greater_than(args: &[LispVal]) -> Result<LispVal, String> {
    if args.len() != 2 {
        return Err("'>' requiere exactamente 2 argumentos".to_string());
    }
    
    match (&args[0], &args[1]) {
        (LispVal::Number(a), LispVal::Number(b)) => Ok(LispVal::Bool(a > b)),
        _ => Err(format!("'>' espera números, recibió {} y {}", args[0], args[1])),
    }
}

// Función principal
fn main() -> io::Result<()> {
    let args: Vec<String> = std::env::args().collect();
    let env = Rc::new(RefCell::new(setup_environment()));
    
    if args.len() > 1 {
        // Modo archivo
        let filename = &args[1];
        let source = fs::read_to_string(filename)?;
        match parse_and_eval(&source, Rc::clone(&env)) {
            Ok(result) => println!("{}", result),
            Err(err) => eprintln!("Error: {}", err),
        }
    } else {
        // Modo REPL
        println!("Mini-Lisp en Rust");
        println!("Escribe 'exit' o Ctrl+C para salir");
        
        let stdin = io::stdin();
        let mut stdout = io::stdout();
        
        loop {
            print!("lisp> ");
            stdout.flush()?;
            
            let mut line = String::new();
            stdin.lock().read_line(&mut line)?;
            
            if line.trim() == "exit" {
                break;
            }
            
            match parse_and_eval(&line, Rc::clone(&env)) {
                Ok(result) => println!("{}", result),
                Err(err) => eprintln!("Error: {}", err),
            }
        }
    }
    
    Ok(())
}

fn setup_environment() -> Environment {
    let mut env = Environment::new();
    
    // Funciones aritméticas
    env.set("+".to_string(), LispVal::Func(add));
    env.set("-".to_string(), LispVal::Func(subtract));
    env.set("*".to_string(), LispVal::Func(multiply));
    env.set("/".to_string(), LispVal::Func(divide));
    
    // Comparaciones
    env.set("=".to_string(), LispVal::Func(equals));
    env.set("<".to_string(), LispVal::Func(less_than));
    env.set(">".to_string(), LispVal::Func(greater_than));
    
    // Constantes
    env.set("#t".to_string(), LispVal::Bool(true));
    env.set("#f".to_string(), LispVal::Bool(false));
    env.set("nil".to_string(), LispVal::Nil);
    
    env
}

fn parse_and_eval(input: &str, env: Rc<RefCell<Environment>>) -> Result<LispVal, String> {
    match parse_expr(input) {
        Ok(expr) => eval(&expr, env),
        Err(err) => Err(err),
    }
}
```
## Cómo funciona
Nuestro intérprete sigue el patrón clásico de procesamiento de lenguajes:
1. **Tokenización**: Dividimos el texto de entrada en componentes individuales (tokens)
2. **Análisis sintáctico**: Transformamos los tokens en un árbol de sintaxis abstracta (AST)
3. **Evaluación**: Recorremos el AST y ejecutamos el código

### Tipos de datos
El núcleo de nuestro intérprete está en el enum `LispVal`, que representa todos los posibles valores en nuestro lenguaje:
- `Symbol`: Identificadores (variables, funciones)
- `Number`: Valores numéricos
- `Bool`: Valores booleanos
- `List`: Listas (que también representan código)
- `Func`: Funciones nativas implementadas en Rust
- `Lambda`: Funciones definidas por el usuario
- `Nil`: Valor nulo

### Entorno
El entorno (`Environment`) almacena variables y funciones en un mapa clave-valor, con soporte para ámbitos anidados a través del campo `outer`.
### Evaluación
El corazón del intérprete es la función `eval` que implementa las reglas de evaluación de Lisp:
- Los números, booleanos y nil se evalúan a sí mismos
- Los símbolos buscan su valor en el entorno
- Las listas se evalúan según reglas especiales:
    - Formas especiales como `quote`, `if`, `define`, `lambda`
    - Invocación de funciones
