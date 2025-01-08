from pathlib import Path
from datetime import timedelta
import os
import environ

# Initialise environment variables
env = environ.Env()
environ.Env.read_env()

# Build paths inside the project like this: BASE_DIR / 'subdir'.
BASE_DIR = Path(__file__).resolve().parent.parent
SECRET_KEY = env("SECRET_KEY")
DEBUG = env("DEBUG")
ALLOWED_HOSTS = ['localhost', '127.0.0.1', 'fattorestreet.com']

# Application definition

INSTALLED_APPS = [
    'users.apps.UsersConfig',
    'portfolio.apps.PortfolioConfig',
    'indexes.apps.IndexesConfig',
    'restaurants.apps.RestaurantsConfig',
    'django.contrib.admin',
    'django.contrib.auth',
    'django.contrib.contenttypes',
    'django.contrib.sessions',
    'django.contrib.messages',
    'django.contrib.staticfiles',
    # Security for communicating with react frontend
    'corsheaders',
    # API library
    'rest_framework',
    # User authentication using djangorestframework-simplejwt
    'rest_framework_simplejwt',
    # celery scheduler
    'django_celery_beat',
    # celery results with django
    'django_celery_results',
    # History
    'simple_history',
]

MIDDLEWARE = [
    'django.middleware.security.SecurityMiddleware',
    # admin uses sessions
    'django.contrib.sessions.middleware.SessionMiddleware',
    'django.middleware.common.CommonMiddleware',
    'django.middleware.csrf.CsrfViewMiddleware',
    'django.contrib.auth.middleware.AuthenticationMiddleware',
    'django.contrib.messages.middleware.MessageMiddleware',
    'django.middleware.clickjacking.XFrameOptionsMiddleware',
    # Security for communicating with react frontend
    'corsheaders.middleware.CorsMiddleware',
    # History
    'simple_history.middleware.HistoryRequestMiddleware',
]

ROOT_URLCONF = 'mysite.urls'

# might be able to delete, idk
TEMPLATES = [
    {
        'BACKEND': 'django.template.backends.django.DjangoTemplates',
        'DIRS': [],
        'APP_DIRS': True,
        'OPTIONS': {
            'context_processors': [
                'django.template.context_processors.debug',
                'django.template.context_processors.request',
                'django.contrib.auth.context_processors.auth',
                'django.contrib.messages.context_processors.messages',
            ],
        },
    },
]

WSGI_APPLICATION = 'mysite.wsgi.application'

if (env("DATABASE") == 'postgresRDS'):
        DATABASES = {
        'default': {
            'ENGINE': 'django.db.backends.postgresql',
            'NAME': 'postgres',
            'USER': env("USERNAME"),
            'PASSWORD': env("PASSWORD_RDS"),
            'HOST': env("HOST_RDS"),
            'PORT': '5432',
        }
    }
elif (env("DATABASE") == 'postgresDocker'):
        DATABASES = {
        'default': {
            'ENGINE': 'django.db.backends.postgresql',
            'NAME': 'postgres',
            'USER': 'postgres',
            'PASSWORD': 'postgres',
            'HOST': 'postgres',
            'PORT': '5432',
        }
    }
        
elif (env("DATABASE") == 'postgresLocal'):
        DATABASES = {
        'default': {
            'ENGINE': 'django.db.backends.postgresql',
            'NAME': 'postgres',
            'USER': 'postgres',
            'PASSWORD': 'postgres',
            'HOST': 'localhost',
            'PORT': '5432',  # Default PostgreSQL port
        }
    }

else: 
        DATABASES = {
        'default': {
            'ENGINE': 'django.db.backends.sqlite3',
            'NAME': BASE_DIR / 'db.sqlite3',
        }
    }

# Password validation
# https://docs.djangoproject.com/en/4.1/ref/settings/#auth-password-validators
AUTH_PASSWORD_VALIDATORS = [
    {
        'NAME': 'django.contrib.auth.password_validation.UserAttributeSimilarityValidator',
    },
    {
        'NAME': 'django.contrib.auth.password_validation.MinimumLengthValidator',
    },
    {
        'NAME': 'django.contrib.auth.password_validation.CommonPasswordValidator',
    },
    {
        'NAME': 'django.contrib.auth.password_validation.NumericPasswordValidator',
    },
]


# Internationalization
# https://docs.djangoproject.com/en/4.1/topics/i18n/
LANGUAGE_CODE = 'en-us'
TIME_ZONE = 'UTC'
USE_I18N = True
USE_TZ = True

STATIC_URL = "static/"
# collect static files in NGINX folder for distribution
STATIC_ROOT = os.path.join(BASE_DIR, "../nginx/static")

DEFAULT_AUTO_FIELD = 'django.db.models.BigAutoField'

# allow NGINX server
CORS_ORIGIN_WHITELIST = [
    'http://localhost:5173',
    'http://localhost',
    'http://localhost:80',
    'http://localhost:3000',
]

CSRF_TRUSTED_ORIGINS = ["https://fattorestreet.com"]

# API library
REST_FRAMEWORK = {
    # Use Django's standard `django.contrib.auth` permissions, or allow read-only access for unauthenticated users.
    'DEFAULT_PERMISSION_CLASSES': [
        'rest_framework.permissions.AllowAny',
    ],
    # JWT authentication
    'DEFAULT_AUTHENTICATION_CLASSES': (
        'rest_framework_simplejwt.authentication.JWTAuthentication',
    )
}

# default is 5 minutes and 1 day
#SIMPLE_JWT = {
#    "ACCESS_TOKEN_LIFETIME": timedelta(minutes=1),
#    "REFRESH_TOKEN_LIFETIME": timedelta(minutes=2),
#}

# might have to be a env variable, this is gonna be a continued problem
CELERY_RESULT_BACKEND = 'redis://localhost:6379'
CELERY_BROKER_URL = 'redis://localhost:6379'
CELERY_BEAT_SCHEDULER = 'django_celery_beat.schedulers.DatabaseScheduler'
CELERY_BROKER_CONNECTION_RETRY_ON_STARTUP = True