import admin from './admin';
import lists from './lists';

export default (token: string, isAdmin: boolean) => ({
  admin: isAdmin ? admin(token) : null,
  lists: lists(token),
});
