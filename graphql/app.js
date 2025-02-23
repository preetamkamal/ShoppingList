import { ApolloServer } from '@apollo/server';
import { startStandaloneServer } from '@apollo/server/standalone';
// import { typeDefs } from './schema';
const typeDefs = `#graphql
  type Product {
    id: ID!
    title: String
    category: String
    price: Float
    rating: Rating
  }
    
  type Query {
    products: [Product]
  }
  
  type Rating {
    rate: Float
    count: Int
  }
`
// server setup

const resolvers = {
    Query: {
        products: async () => {
            try {
                // Fetch products from Fake Store API.
                const res1 = await fetch('https://fakestoreapi.com/products');
                const products1 = await res1.json();

                // Fetch products from DummyJSON.
                const res2 = await fetch('https://dummyjson.com/products');
                const data2 = await res2.json();
                const products2 = data2.products; // DummyJSON returns a "products" array.

                // Transform DummyJSON products so that they match the schema of Fake Store API.
                const transformedProducts2 = products2.map(product => ({
                    id: product.id,
                    title: product.title,
                    category: product.category,
                    price: product.price,
                    rating: {
                        rate: product.rating,
                        count: product.stock  // using stock as a proxy for count in this example
                    }
                }));

                // Merge both arrays of products.
                return [...products1, ...transformedProducts2];
            } catch (error) {
                console.error('Error fetching products:', error);
                return [];
            }
        }
    }
}

const server = new ApolloServer({
    typeDefs,
    resolvers
});


const { url } = await startStandaloneServer(server, {
    listen: { port: 4000 }
});

console.log('server ready at port ', 4000)