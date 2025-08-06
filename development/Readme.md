# Development

The development project is where everything comes together for development. All Clojure parts of the repo are loaded into this single project and allow interactive development running a Repl.

To begin development run a Repl from this project.

## Development

Run nrepl server:
```sh
clj -M:nREPL -m nrepl.cmdline --middleware "[clj-commons.pretty.nrepl/wrap-pretty]"
```

When you start the repl, the `user` namespace located in the _dev_ directory is automatically loaded. It will load and setup everything you need to run the code. 

This includes:
- [clj-reload](https://github.com/tonsky/clj-reload) to reload the code on the fly
- [lazytest](https://github.com/NoahTheDuke/lazytest) to run tests