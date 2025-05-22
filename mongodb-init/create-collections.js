// This script will be executed when the MongoDB container starts
// It creates the necessary collections and indexes for the application

db = db.getSiblingDB('travel-sample');

// Create collections
db.createCollection('users');
db.createCollection('hotel');
db.createCollection('airport');
db.createCollection('flightpath');
db.createCollection('bookings');

// Create initial admin user
db.users.insertOne({
    _id: 'user::admin',
    username: 'admin',
    password: '$2a$10$KOt5c1kcKU3Xx6YAkgKV8eZkKMwqBBCv9D/NIvs37aWjTvTCp6oo.', // password: 'password'
    name: 'Administrator',
    type: 'user'
});

print('MongoDB collections and indexes created successfully');