# Full backend
The web framework django runs on a Gunicorn server, connected utilizing WSGI. A postgreSQL server runs to manage the applications database. A NGINX server is exposed to the outside internet and works as a reverse proxy / load balancer / static conent deliver boy for the system. Vite handles the react frontend development and deployment.

## Starting services
### Trunch Based Development
The repository follows the "Trunk Based Theory" outlined here https://trunkbaseddevelopment.com/. This strategy keeps development close to production and helps eliminate the risk of "environment hell" where its hard to keep track of all your code changes and where they have been pushed up to. This project is particularly primed for one branch because the development team is one individual. The different "environments" specified below are differnt configureations, but all use the same code / branch. Production is released as a stable snapshot of the trunk that doesn't get merged back. 

### Key differences
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
- The postgres container can be run in staging
- RDS can be used with any environment

### Development
Within Portfolio-Manager-Backend/django, run the following bash command.
    python3 manage.py runserver
This starts up a django development server running with an SQLite3 database or the RDS database. This server is used for local development.

### Staging
Within Portfolio-Manager-Backend/django, run the following bash command.
    docker compose up
This starts up the mock production Docker containers including:
- Nginx container serving React frontend and working as a reverse proxy for django
- Gunicorn container running a django DRF based API server
- Postgres container connected to django storing, protecting, and mangaging permanant client data
The production react app points towards fattorestreet.com not localhost, use the vite staging environment variables to assemble the staging front end

### Production / Hosting
- This webapp is hosted for production on AWS fargate. I will perhaps share the Task Definition here if it is safe to do so.
- Production consists of an NGINX and a Gunicorn/django container. A managed RDS server running postgres handles the database and persistant data.
- An AWS application load balancer handles HTTPS

# The long term goal is to be cloud agnostic because I don't want to be tied to a particular cloud vender. 
The RDS server and ALB are both AWS specific and can't be used on a different hosting service. 
They are also the most expensive parts of the system and less control is had over these services compared to the DIY options.
Fargate, an PaaS, is what limits the webapps options and should be eventually phased out by a IaaS option such as EC2.
The application load balancer is the first priority to get rid of. I can configure my own NGINX server and use Certbot / LetsEncrpyt for SSL.