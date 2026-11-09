import api from './client'

/** Полный список пользователей — доступен только супер-администратору. */
export const fetchAllUsers = () => api.get('/users/admin').then((res) => res.data)

/** Удалить пользователя со всеми связанными данными (только супер-администратор). */
export const deleteUser = (userId) => api.delete(`/users/${userId}`)
