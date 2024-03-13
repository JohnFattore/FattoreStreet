# Full Backend
### Note: Django and react-app have dedicated README.md
## Project Overview
The web framework [Django](https://www.djangoproject.com/) runs on a [Gunicorn](https://gunicorn.org/) server, connected utilizing WSGI. A [postgreSQL](https://www.postgresql.org/) server runs to manage the applications database. A [NGINX](https://www.nginx.com/) server works as a reverse proxy / load balancer / static conent deliver boy for the system. [Vite](https://vitejs.dev/) handles the [react](https://react.dev/) frontend development and deployment. [Docker](https://www.docker.com/) is heavily utilized for deployment to cloud services.

### Trunch Based Development
The repository follows the "Trunk Based Theory" outlined here https://trunkbaseddevelopment.com/. This strategy keeps development close to production and helps eliminate the risk of "environment hell" where its hard to keep track of all your code changes and where they have been pushed up to. This project is particularly primed for one branch because the development team is one individual. The different "environments" specified below are differnt configurations, but all use the same code / branch. Production is released as a stable snapshot of the trunk that doesn't get merged back. 

### NO Hungarian Notation
Hungarian Notation was strongly considered earlier in the project i.e. strTicker, numShares, dtmBuy etc. This project is not written with Hungarian Notation because such practices obfuscate the code and make it harder to read. IT also makes refractoring more difficult because if the type changes, the notation will be incorrect. 

### Key differences between Environments
nginx.conf HTTP vs HTTPS
- staging use the local nginx.conf, port 80, no SSL
- (not in use) production use the production nginx.conf, port 443, SSL

nginx.conf 127.0.0.1 vs 172.0.0.1
- staging uses 172.17.0.1 and production uses 127.0.0.1
- Ideally, staging could also use 127.0.0.1 for consistency but im not sure if its possible
- This problem haunts me and I wish i understood it better. I believe AWS does not use a docker network bridge and so containers communicate through localhost ie 127.0.0.1. 
Staging, locally run with compose.yaml, uses a docker bridge network which container traffic must pass through to get to the localhost and so outward traffic through the NGINX container uses this bridges IP address (172.17.0.1)

Database 
- SQLite3 can be run in development
- A postgres container can be run in staging
- RDS can be used with any environment

## Starting each Environment
### Development
To start the django server, cd into Portfolio-Manager-Backend/django and run the following bash command.

    python3 manage.py runserver

This starts up a django development server on port 8000 running with an SQLite3 database or the RDS database.

To start up the react/vite server, cd into Portfolio-Manager-Backend/react-app and run the following bash command.

    npm run dev

This starts up a development server on port 5173 that serves out the react app with API URLS pointing to the django development server.

### Staging
The backend moves to containers in staging, but the front end remains being served by vite.
To start the backend containers, cd into Portfolio-Manager-Backend/django and run the following bash command.
    docker compose up
This starts up the mock production Docker containers including:
- Nginx container serving React frontend and working as a reverse proxy for django
- Gunicorn container running a django DRF based API server
- Postgres container connected to django storing, protecting, and mangaging permanant client data
To start up the react/vite server, cd into Portfolio-Manager-Backend/react-app and run the following bash command.

    npm run staging

This starts up a development server on port 5173 that serves out the react app with API URLS pointing to the Docker containers.

### Production / Hosting
This webapp is hosted for production on AWS. A task definition defines how the Gunicorn and NGINX containers are hosted using AWS fargate. A managed RDS server running postgres handles the database and persistant data. An AWS application load balancer handles HTTPS and provides extra security. The DNS route 53 validates the CA and resolves domain names to ip addresses.

### Dev Ops System Overview 
- All traffic is first resolved by the DNS route 53 and its list of records in the hosted zone, which includes:
1. A CNAME record that validates the CA certificate
2. A A record that directs traffic to the application load balancer (ALB).
- The ALB receives HTTP/HTTPS requests and also serves a view functions:
1. HTTPS requests recieved on port 443 is routed to port 80 of the fargate/ecs service.
2. HTTP requests received on port 80 are routed to port 443, so all subsequent requests use SSL/TLS (HTTPS). 
3. TOD: Requests using the wrong domain, such as fattorestreet.com, are redirect to www.fattorestreet.com.
- The NGINX container is listening on port 80 and serves two functions:
1. Serving out static files such as the react app, static django files, and other media.
2. Acting as a reverse proxy for the Django/Gunicorn server on port 8000. 
- The Gunicorn server communcates via HTTP with the PostgreSQL server running on RDS.

### Domain naming
The CA cert is *.fattore.com, so any subdomain is supported (TODO).
The hosted zone is called fattorestreet.com so all subdomains can be easily accomidated
One unified domain should be used for all traffic (www. or not)

### CI/CD Pipeline
The continous integration, continious deployment pipeline consists of a distinct CI and CD. The CI are automated tests for both the react and django apps. The CD is a .sh file called deploy.sh that does everything between building the production code and deploying it onto AWS.

## Future To Do
#### The long term goal is to be cloud agnostic because I don't want to be tied to a particular cloud vender. 
The RDS server and ALB are both AWS specific and can't be used on a different hosting service. 
They are also the most expensive parts of the system and less control is had over these services compared to the DIY options.
Fargate, an PaaS, is what limits the webapps options and should be eventually phased out by a IaaS option such as EC2.
The application load balancer is the first priority to get rid of. I can configure my own NGINX server and use Certbot / LetsEncrpyt for SSL.