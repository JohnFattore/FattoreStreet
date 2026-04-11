# FattoreStreet.com Updates

The Spring Boot server, which provides commercially free financial data, has been the main focus of the last month. I have been holding off on a ton of changes for about a month now and a big push this weekend was needed to get production back up to speed with development. It is a good reminder why pushing often and keeping development close to production is valuable. Regardless, the Spring Boot app is now robust featuring standardized migrations with Flyway, audit tables with Hibernate Envers, and JWT authentication. Check out an example of the financial data from this server here: [AAPL on FattoreStreet](https://fattorestreet.com/asset/AAPL).

## Technology Spotlight: JWT (JSON Web Tokens)

### What is a JWT?

A JWT is a JSON object made up of three different parts. The first part is the header and contains metadata about the token, including the cryptographic algorithm used. The second part is the payload and contains the claims of the token, such as the user's name and id. These first two parts are base64url encoded, concatenated together with a dot in the middle, and passed through the cryptographic algorithm with a secret key to create the third part, the signature.

### Signed vs Encrypted

A JWT is a signed token, which serves a different purpose than encryption. A JWT can actually be read by anyone, they are merely base64url encoded before being sent over HTTPS. The power is in the signature, which can only be accurately created by someone with the secret key. When a JWT is checked by the server, it attempts to recreate the signature. If anything in the first two parts were tampered with, the signatures will not match and the claims will be invalid. This is in comparison to something like HTTPS, which enables end to end encryption of http request/responses. This type of security ensures the message is unreadable by anyone other than the sender and receiver, but doesn't verify any claims of the message like a signed token would.

### Stateless Authentication

JWTs are considered stateless because they do not require anything to be kept in memory in order for the authentication to work. This is in contrast to session based authentication, which generates what is essentially a password that is stored in the database that the user can use to authenticate their requests. The statelessness of JWTs allows more flexibility for your web app and removes reliance on the database. It allows both my Django and Spring Boot servers to authenticate the same JWTs without sharing a database. All they need to do is sign/check the JWTs with the same secret key. One advantage of session based authentication is that the access can be revoked at any time, while JWTs are active until they expire so short expiration times are important.

### Practical Usage

A user uses their username and password to sign into the app. Upon successful login, the server creates for them a JWT. The react app saves this token as a cookie or in local storage in the browser. It then gets included with any request the user makes to prove to the server they are who they say they are. The JWT gets added as an authentication header on the http request.
