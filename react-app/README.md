## Variable Naming Conventions
 - camelCase
 - Decriptive names, no sentences
 - Strongly typed with TypeScript

### NO Hungarian Notation
Hungarian Notation was strongly considered earlier in the project i.e. strTicker, numShares, dtmBuy etc. This project is not written with Hungarian Notation because such practices obfuscate the code and make it harder to read. IT also makes refractoring more difficult because if the type changes, the notation will be incorrect. 

## Available Scripts
### Sass
To complile you can run either script. Top script compiles sass into css, bottom script will continously compile new changes.

    sass src/styles/custom.scss src/styles/custom.css

or

    sass --watch src/styles/custom.scss:src/styles/custom.css

### Development
    npm run dev

### Rollup, create javascript modules
    npm run build

# React + TypeScript + Vite

This template provides a minimal setup to get React working in Vite with HMR and some ESLint rules.

Currently, two official plugins are available:

- [@vitejs/plugin-react](https://github.com/vitejs/vite-plugin-react/blob/main/packages/plugin-react/README.md) uses [Babel](https://babeljs.io/) for Fast Refresh
- [@vitejs/plugin-react-swc](https://github.com/vitejs/vite-plugin-react-swc) uses [SWC](https://swc.rs/) for Fast Refresh

## Expanding the ESLint configuration

If you are developing a production application, we recommend updating the configuration to enable type aware lint rules:

- Configure the top-level `parserOptions` property like this:

```js
   parserOptions: {
    ecmaVersion: 'latest',
    sourceType: 'module',
    project: ['./tsconfig.json', './tsconfig.node.json'],
    tsconfigRootDir: __dirname,
   },
```

- Replace `plugin:@typescript-eslint/recommended` to `plugin:@typescript-eslint/recommended-type-checked` or `plugin:@typescript-eslint/strict-type-checked`
- Optionally add `plugin:@typescript-eslint/stylistic-type-checked`
- Install [eslint-plugin-react](https://github.com/jsx-eslint/eslint-plugin-react) and add `plugin:react/recommended` & `plugin:react/jsx-runtime` to the `extends` list
