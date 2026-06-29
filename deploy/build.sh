#!/bin/sh
set -e

cd ..

echo "=== Running React tests ==="
cd react-app
npx sass src/styles/custom.scss src/styles/custom.css
npx vitest run
cd ..

echo "=== Running Django tests ==="
cd django
if [ ! -d "venv" ]; then
  echo "Creating Python venv..."
  python3 -m venv venv
fi
. venv/bin/activate
pip install -q --upgrade pip setuptools
pip install -q -r requirements.txt
python3 manage.py test
deactivate
cd ..

echo "=== Running Spring Boot tests ==="
cd springboot
./mvnw test
cd ..

echo "=== All tests passed — starting builds ==="

cd react-app
npm run build
cd ..

cd nginx
docker build -t johnfattore/nginx .
cd ..

cd django
docker build -t johnfattore/django .
cd ..

cd springboot
docker build -t johnfattore/springboot .
cd ..

docker push johnfattore/nginx
docker push johnfattore/django
docker push johnfattore/springboot

echo "Fattore Street is ready to rock and roll"