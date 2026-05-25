# State in the browser
Handling state in the front end is one of the hardest parts of making a dynamic user interface. React provides useState, but managing state this way can get unwieldy as projects grow. Redux provides a system to manage state globally, providing all components with the same source of truth. Instead of juggling state bound to specific components, state can exist globally and be shared across all components.

# Flux
Redux is based on Flux, which is an architectural design pattern built around a one-way data flow: Action → Dispatcher → Store → View. Flux has multiple stores and a dispatcher that orchestrates how the actions interact with each store. Redux simplified this by replacing the multiple stores with a single one. With only one store and one root reducer, there’s no need for a dispatcher to route actions among multiple stores.

# Actions
Actions are plain JavaScript objects that describe what should change. By convention, they are named after the relevant slice and change taking place such as asset/add. They include a payload that gets passed into the function as an argument. These actions are dispatched to the store, which calls the relevant reducer. 

# Reducers
Reducers are the functions that compute the next state. Reducers receive (state, action) and return new state. State should never be mutated by the reducer and a new object should always be returned as the new state. The reducers are pure functions meaning they produce no side effects and output the same result for the same parameters every time. This constraint makes unit tests simple to write and enables Redux's time-travel debugging. Individual slice reducers get combined into a single root reducer.

# Store
The store is a global JavaScript object that holds all the state. The store often consists of individual slices, which are the different subsystems the app might be handling state for. For example, an app might have a user slice and an asset slice, each managing their own separate data. Keeping all slices in one store means every dispatched action updates the whole state atomically. Reducers all see the same prior state, so slices can’t get out of sync.

# Subscribing
Redux includes a pub/sub system that allows state changes to be subscribed to. Callback functions can be provided that run every time the state changes. Redux calls all subscribed functions whenever any state changes, even if the particular state the callback function references didn’t change. Because of this, it is common to check equality of the state at the beginning of the functions to minimize unnecessary renders.

# React Redux and Redux Toolkit (RTK)
Modern Redux tools abstract much of the boilerplate. React Redux provides React hooks that handle the state and update the UI such as useSelector and useDispatch. RTK handles the slice management and action creation. Understanding the basics of Redux provides clarity on what these abstractions are doing. 