import { Form, Col, Row, Alert } from 'react-bootstrap';
import { useForm } from 'react-hook-form';
import * as yup from "yup";
import { yupResolver } from "@hookform/resolvers/yup";

interface IFormInput {
    search: string,
}

export default function RestaurantSearchBar({ setSearch }: { setSearch: (value: string) => void }) {

    const schema = yup.object().shape({
        search: yup.string().required(),
    });

    //useForm is fantastic for handling form state, functions such as onSubmit/onChange/onBlur, validation, and even flexibility for other UI libraries (using Controller)
    const { register, formState: { errors } } = useForm<IFormInput>({
        resolver: yupResolver(schema),
        defaultValues: {
            search: ""
        }
    })

    return (
        <>
            <Form>
                <Row>
                    <Col sm={9}>
                        <Form.Control size="lg" {...register("search", {
                            onChange: (e) => setSearch(e.target.value)
                        })} placeholder='Restaurant Search'/>
                        {errors.search && <Alert variant='danger'>This field is required</Alert>}
                    </Col>
                </Row>
            </Form>
        </>
    );
}