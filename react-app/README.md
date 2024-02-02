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

### Staging
    npm run staging

### Rollup, create javascript modules
    npm run build
- Dist folder is created in NGINX folder 