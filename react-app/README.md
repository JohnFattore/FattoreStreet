# React Front End
This [react](https://react.dev/) app is developed using [Vite](https://vitejs.dev/). Notable libraies utilized inlclude [Vitest](https://vitest.dev/) and [react testing library](https://testing-library.com/) for testing, [react-hook-form](https://react-hook-form.com/) for forms, [Axios](https://axios-http.com/) for http requests, [react-bootstrap](https://react-bootstrap.netlify.app/) for component styling and [react router](https://reactrouter.com/en/main) for routing. [Typescript](https://www.typescriptlang.org/) is utlized because strongly typed variables are preferred and interface usage is encouraged.

## HTTP requests: Axios
Axios is used for all HTTP requests because it provides an easy to use asynchronous HTTP function. Fetch is a notable alternative to Axios that is built into Javascript. Axios is preferred because of its easy of use and support for interceptors. "src/components/AxiosFunctions.tsx contains all the HTTP requests used in the project except for the interceptors which can be found in App.tsx.

## Testing
The test runner utlizied is Vitest because of its smooth integration with Vite and similarities to more popular test runner [Jest](https://jestjs.io/). React testing library includes extra tools utlized for testing. Each page has its own testing file where all components are tested. Axios functions are tested independently and then mocked for the component tests. 

## Forms: React Hook Form
The library react-hook-form includes the function useForm, which is utilitzed for all forms. It helps handling form state, functions such as onSubmit/onChange/onBlur, and validation, It even has flexibility for UI libraries using Controller.

## Routing: React Router
React router is used to simplify the process of naviating to a new page, which needs to be created using javascript.

## UX Design: React Boostrap
React bootstrap contains prestyles components based on the CSS/JS library [Bootstrap](https://getbootstrap.com/). [Sass](https://sass-lang.com/) is used to customize bootstrap to use our own custom colors/designs.
To complile the custom CSS sheet, you can run either script. Top script compiles sass into css, bottom script will continously compile new changes.

    sass src/styles/custom.scss src/styles/custom.css

or (preferred)

    sass --watch src/styles/custom.scss:src/styles/custom.css

## Serving app for each environment
More details about deployment can be seen on the main README.md.
### Development
    npm run dev

### Staging
    npm run staging

### Production: Create javascript modules with Rollup
    npm run build