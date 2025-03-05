import Container from 'react-bootstrap/Container';
import Nav from 'react-bootstrap/Nav';
import Navbar from 'react-bootstrap/Navbar';
import LogoutButton from './LogoutButton';
import { useDispatch, useSelector } from "react-redux";
import { AppDispatch, RootState } from '../main';
import { Button } from 'react-bootstrap';
import { setUserDarkMode } from '../reducers/userReducer';

export default function TopNavigation() {

  const dispatch = useDispatch<AppDispatch>();
  const { access, darkMode } = useSelector((state: RootState) => state.user);

  return (
    <Navbar variant="light" expand="lg">
      <Container>
        <Navbar.Brand href="/">Home</Navbar.Brand>
        <Navbar.Toggle aria-controls="basic-navbar-nav" />
        <Navbar.Collapse id="basic-navbar-nav">
          <Nav className="me-auto">
            <Nav.Link href="portfolio">Portfolio</Nav.Link>
            <Nav.Link href="watchlist">Watch List</Nav.Link>
            <Nav.Link href="education">Suggestions</Nav.Link>
            <Nav.Link href="register">Register</Nav.Link>
            {/*<Nav.Link href="outliers">Outliers</Nav.Link>*/}
            <Nav.Link href="restaurants">Restaurants</Nav.Link>
            <Nav.Link href="reviews">Reviews</Nav.Link>
            <Nav.Link href="entertainment">Entertainment</Nav.Link>
            <Nav.Link href="chatbot">Chatbot</Nav.Link>
            {access && <LogoutButton/>}
            <Button onClick={() => dispatch(setUserDarkMode(!darkMode))}> Change Theme </Button>
          </Nav>
        </Navbar.Collapse>
      </Container>
    </Navbar>
  );
}