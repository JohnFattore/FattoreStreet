import csv

data = [
    {'name': 'Nikhil', 'branch': 'COE', 'year': 2, 'cgpa': 9.0},
    {'name': 'Sanchit', 'branch': 'COE', 'year': 2, 'cgpa': 9.1},
    {'name': 'Aditya', 'branch': 'IT', 'year': 2, 'cgpa': 9.3},
    {'name': 'Sagar', 'branch': 'SE', 'year': 1, 'cgpa': 9.5},
    {'name': 'Prateek', 'branch': 'MCE', 'year': 3, 'cgpa': 7.8},
    {'name': 'Sahil', 'branch': 'EP', 'year': 2, 'cgpa': 9.1}
]

with open('university_records.csv', 'w', newline='') as csvfile:
    fieldnames = ['name', 'branch', 'year', 'cgpa']
    writer = csv.DictWriter(csvfile, fieldnames=fieldnames)
    writer.writeheader()
    writer.writerows(data)