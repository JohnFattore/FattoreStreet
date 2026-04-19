# Django's Model Template View System

Django is a Python based, open source web framework that is famous for its batteries included approach. At its core, Django is a MTV (Model, Template, View) system, Django's name for an MVC (Model, View, Controller). They both follow the same pattern of defining a database model, creating user interfaces (templates/views), then adding a request handling layer in between them (view/controller). This set up creates a simple, but powerful way to design websites with server-side rendering. This removes the need for a separate front end app that can overcomplicate basic apps. For this post, I built a simple app for media recommendations you can see here: https://fattorestreet.com/django/entertainment/

## Models
Models are central to the Django framework philosophy and are often the first part of the process. A Django model is a Python class defined in models.py that acts as a blueprint. It is a level of abstraction from the database that allows the developer to never touch SQL directly. My recommendation model includes fields such as title, artist, and type. This model definition enables compatibility with the Object-Relational Mapper (ORM), a tool that maps a database table to an object and allows for writing queries right in code. Retrieving all recommendation entries with the type Music as python objects is as easy as:
music_recommendations = Recommendation.objects.filter(type="MUSIC")

## Templates:
The templates are written in Django Template Language (DTL), which is HTML extended with special syntax. This special syntax allows for dynamic data to be injected into the page. For example, variable tags {{ variable }} allow for the displaying of variables. Template tags {% for rec in music_recommendations %} allow for some logic, such as looping through a list. My example leverages these tags to create a page that lists all the music entries, displaying the artist and title with the following DTL code: (music_list.html)
```
{% for rec in music_recommendations %}
    <h1>{{ rec.title }}</h1>
    <p>{{ rec.artist }}</p>
{% endfor %}
```
## Views:
The views are what hook together the models with the templates and are defined in the views.py file. When a request is sent to the Django server, it first looks at the urls.py file. Django uses this file to map URL patterns to views. For this example, that would look something like this:
```
path('music/', views.music_list, name='music-list'). 
```
The request then hits the view, which executes the logic to process the request and return the response. In this example, the view runs the ORM query fetching all the music recommendations, then passes that context to the template. A basic function based view would look like this:
```
def music_list(request):
    music_recommendations = Recommendation.objects.filter(type="MUSIC")
    return render(
        request,
        "music_list.html",
        {"music_recommendations": music_recommendations})
```
## Conclusion:
Together, these three pieces give you a complete web app in a single codebase. Django is a great starting point for anyone new to web development. It contains tools for all the fundamental aspects of a web app in one easy to use package. 