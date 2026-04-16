from django.db import models

class Recommendation(models.Model):                                                                                                                                                                                                                                                                                         
    class Type(models.TextChoices):
        BOOK   = "BOOK",   "Book"                                                                                                                                                                                                                                                                                           
        MOVIE  = "MOVIE",  "Movie"                                                                                                                                                                                                                                                                                          
        SHOW   = "SHOW",   "TV Show"
        MUSIC  = "MUSIC",  "Music"                                                                                                                                                                                                                                                                                          
        PODCAST = "PODCAST", "Podcast"
        GAME   = "GAME",   "Game"
        WEBSITE   = "WEBSITE",   "Website"
                
    type   = models.CharField(max_length=10, choices=Type.choices)                                                                                                                                                                                                                                                          
    title  = models.CharField(max_length=200)
    artist = models.CharField(max_length=200)   # author/director/band/etc.
    notes  = models.TextField(blank=True)
    year   = models.PositiveSmallIntegerField(null=True, blank=True)                                                                                                                                                                                                                                                 
                                                                                                                                                                                                                                                                                                                            
    created_at = models.DateTimeField(auto_now_add=True)                                                                                                                                                                                                                                                                    
                                                                                                                                                                                                                                                                                                                            
    class Meta:                                                                                                                                                                                                                                                                                                             
        ordering = ["-created_at"]
        constraints = [
            models.UniqueConstraint(
                fields=["type", "title", "artist"],
                name="uniq_recommendation",                                                                                                                                                                                                                                                                                 
            ),
        ]                                                                                                                                                                                                                                                                                                                   
                
    def __str__(self):
        return f"{self.title} — {self.artist} ({self.get_type_display()})"